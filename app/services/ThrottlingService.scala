/*
 * Copyright 2015 HM Revenue & Customs
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

import cryptography.Encryption
import model.Location._
import model._
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.json._
import play.api.mvc.{AnyContent, Request}
import play.api.{Configuration, Logger, Play}
import repositories.RoutingCacheRepository
import uk.gov.hmrc.cache.model.Id
import uk.gov.hmrc.mongo.BSONBuilderHelpers
import uk.gov.hmrc.play.frontend.auth.AuthContext

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Success}

trait ThrottlingService extends BSONBuilderHelpers {

  def random: Random

  def encryption: Encryption

  val throttlingEnabled = Play.configuration.getBoolean("throttling.enabled").getOrElse(false)
  val stickyRoutingEnabled = Play.configuration.getBoolean("sticky-routing.enabled").getOrElse(false)

  val longLiveCacheExpirationTime = Play.configuration.getString("sticky-routing.long-live-cache-expiration-time")
  val shortLiveCacheDuration = Play.configuration.getInt("sticky-routing.short-live-cache-duration")

  val documentExpirationTime = Map(
    (PersonalTaxAccount, PersonalTaxAccount) -> longLiveCacheExpirationTime.map(t => Instant(DateTime.parse(t))),
    (BusinessTaxAccount, BusinessTaxAccount) -> shortLiveCacheDuration.map(t => Duration(t)),
    (PersonalTaxAccount, BusinessTaxAccount) -> shortLiveCacheDuration.map(t => Duration(t))
  )

  val cacheDestinations = Map(
    PersonalTaxAccount -> PersonalTaxAccount,
    BusinessTaxAccount -> BusinessTaxAccount,
    WelcomeBTA -> BusinessTaxAccount,
    WelcomePTA -> PersonalTaxAccount
  )

  def routingCacheRepository: RoutingCacheRepository

  def hourlyLimitService: HourlyLimitService

  private def findConfigurationFor(location: LocationType)(implicit request: Request[AnyContent]): Configuration = {

    val suffix: String = getLocationSuffix(location, request)

    Play.configuration.getConfig(s"throttling.locations.${location.name}$suffix")
      .getOrElse(
        Play.configuration.getConfig(s"throttling.locations.${location.group}$suffix")
          .getOrElse(Configuration.empty)
      )
  }

  def getLocationSuffix(location: LocationType, request: Request[AnyContent]): String = {
    request.session.data.contains("token") match {
      case true if location.group == LocationGroup.PTA => "-gg"
      case false if location.group == LocationGroup.PTA => "-verify"
      case _ => ""
    }
  }

  private def findFallbackFor(configurationForLocation: Configuration, location: LocationType): String = {
    val fallbackName: String = configurationForLocation.getString("fallback").getOrElse(location.name)
    if (location == Location.WelcomePTA && fallbackName == Location.BusinessTaxAccount.name) {
      Location.WelcomeBTA.name
    } else {
      fallbackName
    }
  }

  private def findPercentageToThrottleFor(configurationForLocation: Configuration): Option[Int] = {
    configurationForLocation.getString("percentageBeToThrottled").map(_.toInt)
  }

  def findLocationByName(fallbackLocationName: String): Option[LocationType] = {
    Location.locations.get(fallbackLocationName)
  }

  def throttle(location: LocationType, auditContext: TAuditContext)(implicit request: Request[AnyContent], authContext: AuthContext, ex: ExecutionContext): Future[LocationType] = {

    val userId = encryption.getSha256(authContext.user.userId)

    stickyRoutingEnabled match {
      case true =>
        routingCacheRepository.findById(Id(userId)).flatMap { optionalCache =>

          optionalCache.flatMap { cache =>

            cache.data.map { data =>
              val jsResult = Json.fromJson[RoutingInfo](data)
              jsResult match {
                case JsSuccess(routingInfo, _) =>
                  val followingPreviouslyRoutedDestination = Location.locations.get(routingInfo.routedDestination).contains(location) && routingInfo.expirationTime.isAfterNow
                  val throttledLocation = followingPreviouslyRoutedDestination match {
                    case true =>
                      val finalLocation = Location.locations.get(routingInfo.throttledDestination).get
                      auditContext.setThrottlingDetails(ThrottlingAuditContext(throttlingPercentage = None, location != finalLocation, location, throttlingEnabled, followingPreviouslyRoutedDestination))
                      Future(finalLocation)
                    case false =>
                      doThrottling(location, auditContext, userId)
                  }

                  getThrottlingResult(location, throttledLocation, userId)
                case JsError(e) =>
                  Logger.error(s"Error reading document $e")
                  Future(location)
              }
            }

          }.getOrElse {
            val throttledLocation = doThrottling(location, auditContext, userId)
            getThrottlingResult(location, throttledLocation, userId)
          }
        }
      case false =>
        val throttledLocation = doThrottling(location, auditContext, userId)
        getThrottlingResult(location, throttledLocation, userId)
    }
  }

  case class DoThrottlingResult(location: LocationType, throttlingPercentage: Int)

  def doThrottling(location: LocationType, auditContext: TAuditContext, userId: String)(implicit request: Request[AnyContent], ex: ExecutionContext): Future[LocationType] = {
    throttlingEnabled match {
      case false =>
        auditContext.setThrottlingDetails(ThrottlingAuditContext(throttlingPercentage = None, throttled = false, location, throttlingEnabled, followingPreviouslyRoutedDestination = false))
        Future(location)
      case true =>
        val configurationForLocation = findConfigurationFor(location)
        val throttlingChanceOption = findPercentageToThrottleFor(configurationForLocation)
        val throttlingChance = throttlingChanceOption.getOrElse(0)
        val randomNumber = random.nextInt(100) + 1
        val finalLocation = randomNumber match {
          case x if x <= throttlingChance => Future(findLocationByName(findFallbackFor(configurationForLocation, location)).getOrElse(location))
          case _ =>
            val fallbackLocation = findLocationByName(findFallbackFor(configurationForLocation, location)).getOrElse(location)
            hourlyLimitService.applyHourlyLimit(location, fallbackLocation, userId, findConfigurationFor(location))
        }

        finalLocation.andThen {
          case Success(result) => auditContext.setThrottlingDetails(ThrottlingAuditContext(throttlingPercentage = throttlingChanceOption, location != result, location, throttlingEnabled, followingPreviouslyRoutedDestination = false))
        }
    }
  }

  def getThrottlingResult(location: LocationType, futureThrottledLocation: Future[LocationType], utr: String)(implicit executionContext: ExecutionContext): Future[LocationType] = {
    stickyRoutingEnabled match {
      case true =>
        futureThrottledLocation.map { throttledLocation =>
          val throttlingResult: Option[LocationType] = for {
            routedDestination <- cacheDestinations.get(location)
            throttledDestination <- cacheDestinations.get(throttledLocation)
            documentExpirationTime <- documentExpirationTime.get((routedDestination, throttledDestination)).flatMap(identity)
          } yield {
              val expirationTime: DateTime = documentExpirationTime.getExpirationTime
              routingCacheRepository.createOrUpdate(utr, "routingInfo", Json.toJson(RoutingInfo(routedDestination.name, throttledDestination.name, expirationTime)))
              throttledLocation
            }
          throttlingResult.getOrElse(throttledLocation)
        }
      case false =>
        futureThrottledLocation
    }
  }
}

object ThrottlingService extends ThrottlingService {

  override val random = Random

  override val routingCacheRepository: RoutingCacheRepository = RoutingCacheRepository()

  override def encryption: Encryption = Encryption

  override def hourlyLimitService: HourlyLimitService = HourlyLimitService
}
