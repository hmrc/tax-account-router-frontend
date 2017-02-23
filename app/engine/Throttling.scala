/*
 * Copyright 2017 HM Revenue & Customs
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

package engine

import cats.data.WriterT
import connector.InternalUserIdentifier
import model.Locations.{BusinessTaxAccount, PersonalTaxAccount}
import model._
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.{AnyContent, Request}
import play.api.{Configuration, Logger, Play}
import repositories.RoutingCacheRepository
import services.{Duration, HourlyLimitService, Instant}
import uk.gov.hmrc.cache.model.Id

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Random, Success}

trait Throttling {

  def random: Random

  def routingCacheRepository: RoutingCacheRepository

  def hourlyLimitService: HourlyLimitService

  def configuration: Configuration

  val throttlingEnabled = Play.configuration.getBoolean("throttling.enabled").getOrElse(false)
  val stickyRoutingEnabled = Play.configuration.getBoolean("sticky-routing.enabled").getOrElse(false)

  val longLiveCacheExpirationTime = Play.configuration.getString("sticky-routing.long-live-cache-expiration-time")
  val shortLiveCacheDuration = Play.configuration.getInt("sticky-routing.short-live-cache-duration")

  val documentExpirationTime = Map(
    (PersonalTaxAccount, PersonalTaxAccount) -> longLiveCacheExpirationTime.map(t => Instant(DateTime.parse(t))),
    (BusinessTaxAccount, BusinessTaxAccount) -> shortLiveCacheDuration.map(t => Duration(t)),
    (PersonalTaxAccount, BusinessTaxAccount) -> shortLiveCacheDuration.map(t => Duration(t))
  )

  def doThrottle(currentResult: EngineResult, ruleContext: RuleContext): EngineResult = {

    def createOrUpdateRoutingCache(location: Location, throttledLocation: Location, userIdentifier: String) = {
      documentExpirationTime.get((location, throttledLocation)).flatten.map { documentExpirationTime =>
        val expirationTime: DateTime = documentExpirationTime.getExpirationTime
        routingCacheRepository.createOrUpdate(userIdentifier, "routingInfo", Json.toJson(RoutingInfo(location.name, throttledLocation.name, expirationTime)))
        throttledLocation
      }
    }

    import AuditInfo._

    def location(userIdentifier: InternalUserIdentifier): EngineResult = {
      if (stickyRoutingEnabled) {

        currentResult.flatMap { initialLocation =>

          WriterT {
            for {
              cacheResult <- routingCacheRepository.findById(Id(userIdentifier))
              auditInfo <- currentResult.written
              result <- cacheResult match {
                case Some(cache) =>
                  cache.data.map { data =>
                    val jsResult = Json.fromJson[RoutingInfo](data)
                    jsResult match {
                      case JsSuccess(routingInfo, _) =>
                        val stickyRoutingApplied = Locations.find(routingInfo.routedDestination).contains(initialLocation) && routingInfo.expirationTime.isAfterNow
                        val throttledLocation = stickyRoutingApplied match {
                          case true =>
                            val finalLocation = Locations.find(routingInfo.throttledDestination).get
                            val auditInfoWithThrottlingInfo = auditInfo.copy(throttlingInfo = Some(ThrottlingInfo(throttlingPercentage = None, initialLocation != finalLocation, initialLocation, throttlingEnabled, stickyRoutingApplied)))
                            Future.successful(auditInfoWithThrottlingInfo, finalLocation)
                          case false =>
                            throttle(currentResult, ruleContext).run
                        }

                        throttledLocation andThen {
                          case Success((_, tLocation)) => createOrUpdateRoutingCache(initialLocation, tLocation, userIdentifier)
                        }
                        throttledLocation
                      case JsError(e) =>
                        Logger.error(s"Error reading document $e")
                        currentResult.run
                    }
                  }.getOrElse(throttle(currentResult, ruleContext).run)
                case _ => throttle(currentResult, ruleContext).run
              }
            } yield result
          }
        }
      } else {
        throttle(currentResult, ruleContext)
      }
    }

    WriterT {
      ruleContext.internalUserIdentifier map {
        case Some(identifier) =>
          location(identifier)
        case _ =>
          currentResult
      } flatMap (_ run)
    }
  }

  def throttle(currentResult: EngineResult, ruleContext: RuleContext): EngineResult = {

    def configurationForLocation(location: Location, request: Request[AnyContent]): Configuration = {

      def getLocationSuffix(location: Location, request: Request[AnyContent]): String = {
        location match {
          case PersonalTaxAccount =>
            if (request.session.data.contains("token")) "-gg"
            else "-verify"
          case _ => ""
        }
      }

      val suffix = getLocationSuffix(location, request)
      configuration.getConfig(s"throttling.locations.${location.name}$suffix").getOrElse(Configuration.empty)
    }

    import cats.instances.all._
    import cats.syntax.flatMap._
    import cats.syntax.functor._

    def throttlePercentage: (Configuration) => Int = configurationForLocation => {
      configurationForLocation.getString("percentageBeToThrottled").map(_.toInt).getOrElse(0)
    }

    def hourlyLimit(hourOfDay: Int): (Configuration) => Option[Int] = configurationForLocation => {
      configurationForLocation.getInt(s"hourlyLimit.$hourOfDay").orElse(configurationForLocation.getInt("hourlyLimit.other"))
    }

    def findFallbackFor(location: Location): (Configuration) => Location = configurationForLocation => {
      val fallback = for {
        fallbackName <- configurationForLocation.getString("fallback")
        fallback <- Locations.find(fallbackName)
      } yield fallback
      fallback.getOrElse(location)
    }

    def throttle(auditInfo: AuditInfo, location: Location, userId: InternalUserIdentifier): (Configuration) => (AuditInfo, Location) =
      for {
        percentage <- throttlePercentage
        throttleDestination <- findFallbackFor(location)
        hourOfDay = DateTime.now().getHourOfDay
        hourlyLimit <- hourlyLimit(hourOfDay)
        randomNumber = random.nextInt(100) + 1
        throttlingInfo = ThrottlingInfo(throttlingPercentage = Some(percentage), location != throttleDestination, location, throttlingEnabled, stickyRoutingApplied = false)
      } yield {
        if (randomNumber <= percentage) {
          (auditInfo.copy(throttlingInfo = Some(throttlingInfo)), throttleDestination)
        } else {
          hourlyLimitService.applyHourlyLimit(location, throttleDestination, userId, hourlyLimit, hourOfDay)
          (auditInfo.copy(throttlingInfo = Some(throttlingInfo)), throttleDestination)
        }
      }

    currentResult flatMap { location =>

      val result: Future[(AuditInfo, Location)] = for {
        userIdentifier <- ruleContext.internalUserIdentifier
        auditInfo <- currentResult.written
      } yield userIdentifier match {
        case None => (auditInfo, location)
        case Some(userId) if throttlingEnabled =>
          throttle(auditInfo, location, userId)(configurationForLocation(location, ruleContext.request_))
        case Some(_) =>
          val throttlingInfo = ThrottlingInfo(throttlingPercentage = None, throttled = false, location, throttlingEnabled, stickyRoutingApplied = false)
          (auditInfo.copy(throttlingInfo = Some(throttlingInfo)), location)
      }

      WriterT(result)
    }
  }
}

object Throttling extends Throttling {

  override lazy val random: Random = Random

  override lazy val routingCacheRepository: RoutingCacheRepository = RoutingCacheRepository()

  override lazy val hourlyLimitService: HourlyLimitService = HourlyLimitService

  override val configuration: Configuration = Play.configuration
}
