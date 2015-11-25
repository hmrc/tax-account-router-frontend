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
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.{AnyContent, Request}
import play.api.{Configuration, Logger, Play}
import repositories.RoutingCacheRepository
import uk.gov.hmrc.cache.model.Id
import uk.gov.hmrc.mongo.BSONBuilderHelpers
import uk.gov.hmrc.play.frontend.auth.AuthContext

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait ThrottlingService extends BSONBuilderHelpers {

  def random: Random

  def encryption: Encryption

  val throttlingEnabled = Play.configuration.getBoolean("throttling.enabled").getOrElse(false)
  val stickyRoutingEnabled = Play.configuration.getBoolean("sticky-routing.enabled").getOrElse(false)

  val longLiveCookieExpirationTime = Play.configuration.getString("sticky-routing.long-live-cookie-expiration-time")
  val shortLiveCookieDuration = Play.configuration.getInt("sticky-routing.short-live-cookie-duration")

  val documentExpirationTime = Map(
    (PersonalTaxAccount, PersonalTaxAccount) -> longLiveCookieExpirationTime.map(t => Instant(DateTime.parse(t))),
    (BusinessTaxAccount, BusinessTaxAccount) -> shortLiveCookieDuration.map(t => Duration(t)),
    (PersonalTaxAccount, BusinessTaxAccount) -> shortLiveCookieDuration.map(t => Duration(t))
  )

  val cookieValues = Map(
    PersonalTaxAccount -> PersonalTaxAccount,
    BusinessTaxAccount -> BusinessTaxAccount,
    WelcomeBTA -> BusinessTaxAccount,
    WelcomePTA -> PersonalTaxAccount
  )

  def routingCacheRepository: RoutingCacheRepository

  private def findConfigurationFor(location: LocationType)(implicit request: Request[AnyContent]): Configuration = {

    val suffix = request.session.data.contains("token") match {
      case true if location.group == LocationGroup.PTA => "-gg"
      case false if location.group == LocationGroup.PTA => "-verify"
      case _ => ""
    }

    Play.configuration.getConfig(s"throttling.locations.${location.name}$suffix")
      .getOrElse(
        Play.configuration.getConfig(s"throttling.locations.${location.group}$suffix")
          .getOrElse(Configuration.empty)
      )
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
        routingCacheRepository.findById(Id(userId)).map { optionalCache =>

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
                      finalLocation
                    case false =>
                      doThrottling(location, auditContext)
                  }

                  getThrottlingResult(location, throttledLocation, userId)
                case JsError(e) =>
                  Logger.error(s"Error reading document $e")
                  location
              }
            }

          }.getOrElse {
            val throttledLocation: LocationType = doThrottling(location, auditContext)
            getThrottlingResult(location, throttledLocation, userId)
          }
        }
      case false =>
        val throttledLocation: LocationType = doThrottling(location, auditContext)
        Future(getThrottlingResult(location, throttledLocation, userId))
    }
  }

  case class DoThrottlingResult(location: LocationType, throttlingPercentage: Int)

  def doThrottling(location: LocationType, auditContext: TAuditContext)(implicit request: Request[AnyContent]): LocationType = {
    throttlingEnabled match {
      case false =>
        auditContext.setThrottlingDetails(ThrottlingAuditContext(throttlingPercentage = None, throttled = false, location, throttlingEnabled, followingPreviouslyRoutedDestination = false))
        location
      case true =>
        val configurationForLocation: Configuration = findConfigurationFor(location)
        val throttlingChanceOption = findPercentageToThrottleFor(configurationForLocation)
        val throttlingChance: Int = throttlingChanceOption.getOrElse(0)
        val randomNumber = random.nextInt(100) + 1
        val finalLocation: LocationType = randomNumber match {
          case x if x <= throttlingChance => findLocationByName(findFallbackFor(configurationForLocation, location)).getOrElse(location)
          case _ => location
        }
        auditContext.setThrottlingDetails(ThrottlingAuditContext(throttlingPercentage = throttlingChanceOption, location != finalLocation, location, throttlingEnabled, followingPreviouslyRoutedDestination = false))
        finalLocation
    }
  }

  def getThrottlingResult(location: LocationType, throttledLocation: LocationType, utr: String)(implicit executionContext: ExecutionContext): LocationType = {
    stickyRoutingEnabled match {
      case true =>
        val throttlingResult: Option[LocationType] = for {
          routedDestination <- cookieValues.get(location)
          throttledDestination <- cookieValues.get(throttledLocation)
          documentExpirationTime <- documentExpirationTime.get((routedDestination, throttledDestination)).flatMap(identity)
        } yield {
            val expirationTime: DateTime = documentExpirationTime.getExpirationTime
            routingCacheRepository.createOrUpdate(utr, "routingInfo", Json.toJson(RoutingInfo(routedDestination.name, throttledDestination.name, expirationTime)))
            throttledLocation
          }
        throttlingResult.getOrElse(throttledLocation)
      case false =>
        throttledLocation
    }
  }
}

object ThrottlingService extends ThrottlingService {

  override val random = Random

  override val routingCacheRepository: RoutingCacheRepository = RoutingCacheRepository()

  override def encryption: Encryption = Encryption
}
