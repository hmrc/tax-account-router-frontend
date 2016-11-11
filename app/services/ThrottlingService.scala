/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import connector.InternalUserIdentifier
import model.Locations._
import model.{Location, _}
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.json._
import play.api.mvc.{AnyContent, Request}
import play.api.{Configuration, Logger, Play}
import repositories.RoutingCacheRepository
import uk.gov.hmrc.cache.model.Id
import uk.gov.hmrc.mongo.BSONBuilderHelpers

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Success}

trait ThrottlingService extends BSONBuilderHelpers {

  def random: Random

  val throttlingEnabled = Play.configuration.getBoolean("throttling.enabled").getOrElse(false)
  val stickyRoutingEnabled = Play.configuration.getBoolean("sticky-routing.enabled").getOrElse(false)

  val longLiveCacheExpirationTime = Play.configuration.getString("sticky-routing.long-live-cache-expiration-time")
  val shortLiveCacheDuration = Play.configuration.getInt("sticky-routing.short-live-cache-duration")

  val documentExpirationTime = Map(
    (PersonalTaxAccount, PersonalTaxAccount) -> longLiveCacheExpirationTime.map(t => Instant(DateTime.parse(t))),
    (BusinessTaxAccount, BusinessTaxAccount) -> shortLiveCacheDuration.map(t => Duration(t)),
    (PersonalTaxAccount, BusinessTaxAccount) -> shortLiveCacheDuration.map(t => Duration(t))
  )

  def routingCacheRepository: RoutingCacheRepository

  def hourlyLimitService: HourlyLimitService

  private def findConfigurationFor(location: Location)(implicit request: Request[AnyContent]): Configuration = {

    val suffix: String = getLocationSuffix(location, request)

    Play.configuration.getConfig(s"throttling.locations.${location.name}$suffix").getOrElse(Configuration.empty)
  }

  def getLocationSuffix(location: Location, request: Request[AnyContent]): String = {
    request.session.data.contains("token") match {
      case true if location.name == PersonalTaxAccount.name => "-gg"
      case false if location.name == PersonalTaxAccount.name => "-verify"
      case _ => ""
    }
  }

  private def findFallbackFor(configurationForLocation: Configuration, location: Location): String = {
    configurationForLocation.getString("fallback").getOrElse(location.name)
  }

  private def findPercentageToThrottleFor(configurationForLocation: Configuration): Option[Int] = {
    configurationForLocation.getString("percentageBeToThrottled").map(_.toInt)
  }

  def throttle(initialLocation: Location, auditContext: TAuditContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], ex: ExecutionContext): Future[Location] = {

    def location(userIdentifier: InternalUserIdentifier) = {

      stickyRoutingEnabled match {
        case true =>
          routingCacheRepository.findById(Id(userIdentifier)).flatMap { optionalCache =>

            optionalCache.flatMap { cache =>

              cache.data.map { data =>
                val jsResult = Json.fromJson[RoutingInfo](data)
                jsResult match {
                  case JsSuccess(routingInfo, _) =>
                    val stickyRoutingApplied = Locations.find(routingInfo.routedDestination).contains(initialLocation) && routingInfo.expirationTime.isAfterNow
                    val throttledLocation = stickyRoutingApplied match {
                      case true =>
                        val finalLocation = Locations.find(routingInfo.throttledDestination).get
                        auditContext.setThrottlingDetails(ThrottlingAuditContext(throttlingPercentage = None, initialLocation != finalLocation, initialLocation, throttlingEnabled, stickyRoutingApplied))
                        Future(finalLocation)
                      case false =>
                        doThrottling(initialLocation, auditContext, userIdentifier)
                    }

                    throttledLocation.andThen {
                      case Success(tLocation) => createOrUpdateRoutingCache(initialLocation, tLocation, userIdentifier)
                    }
                  case JsError(e) =>
                    Logger.error(s"Error reading document $e")
                    Future(initialLocation)
                }
              }

            }.getOrElse {
              val throttledLocation = doThrottling(initialLocation, auditContext, userIdentifier)
              throttledLocation.andThen {
                case Success(tLocation) => createOrUpdateRoutingCache(initialLocation, tLocation, userIdentifier)
              }
            }
          }
        case false =>
          val throttledLocation = doThrottling(initialLocation, auditContext, userIdentifier)
          throttledLocation.andThen {
            case Success(tLocation) => createOrUpdateRoutingCache(initialLocation, tLocation, userIdentifier)
          }
      }
    }
    ruleContext.internalUserIdentifier.flatMap(location)
  }

  def doThrottling(location: Location, auditContext: TAuditContext, userIdentifier: InternalUserIdentifier)(implicit request: Request[AnyContent], ex: ExecutionContext): Future[Location] = {
    throttlingEnabled match {
      case false =>
        auditContext.setThrottlingDetails(ThrottlingAuditContext(throttlingPercentage = None, throttled = false, location, throttlingEnabled, stickyRoutingApplied = false))
        Future(location)
      case true =>
        val configurationForLocation = findConfigurationFor(location)
        val throttlingChanceOption = findPercentageToThrottleFor(configurationForLocation)
        val throttlingChance = throttlingChanceOption.getOrElse(0)
        val randomNumber = random.nextInt(100) + 1
        val finalLocation = randomNumber match {
          case x if x <= throttlingChance => Future(Locations.find(findFallbackFor(configurationForLocation, location)).getOrElse(location))
          case _ =>
            val fallbackLocation = Locations.find(findFallbackFor(configurationForLocation, location)).getOrElse(location)
            hourlyLimitService.applyHourlyLimit(location, fallbackLocation, userIdentifier, findConfigurationFor(location))
        }

        finalLocation.andThen {
          case Success(result) => auditContext.setThrottlingDetails(ThrottlingAuditContext(throttlingPercentage = throttlingChanceOption, location != result, location, throttlingEnabled, stickyRoutingApplied = false))
        }
    }
  }

  def createOrUpdateRoutingCache(location: Location, throttledLocation: Location, userIdentifier: String) = {
    if (stickyRoutingEnabled) {
      documentExpirationTime.get((location, throttledLocation)).flatten.map { documentExpirationTime =>
        val expirationTime: DateTime = documentExpirationTime.getExpirationTime
        routingCacheRepository.createOrUpdate(userIdentifier, "routingInfo", Json.toJson(RoutingInfo(location.name, throttledLocation.name, expirationTime)))
        throttledLocation
      }
    }
  }
}

object ThrottlingService extends ThrottlingService {

  override val random = Random

  override val routingCacheRepository: RoutingCacheRepository = RoutingCacheRepository()

  override def hourlyLimitService: HourlyLimitService = HourlyLimitService
}
