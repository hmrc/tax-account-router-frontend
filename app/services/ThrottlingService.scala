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

import model.Location._
import model.{Location, TAuditContext, ThrottlingAuditContext}
import org.joda.time.DateTime
import play.api.Play.current
import play.api.mvc.{AnyContent, Cookie, DiscardingCookie, Request}
import play.api.{Configuration, Play}

import scala.util.Random

trait ThrottlingService {

  def random: Random

  val throttlingEnabled = Play.configuration.getBoolean("throttling.enabled").getOrElse(false)
  val stickyRoutingEnabled = Play.configuration.getBoolean("sticky-routing.enabled").getOrElse(false)

  val longLiveCookieExpirationTime = Play.configuration.getString("sticky-routing.long-live-cookie-expiration-time")
  val shortLiveCookieDuration = Play.configuration.getInt("sticky-routing.short-live-cookie-duration")

  val cookieMaxAge = Map(
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

  private def findConfigurationFor(location: LocationType)(implicit request: Request[AnyContent]): Configuration = {

    val suffix = request.session.data.contains("token") match {
      case true if location == Location.PersonalTaxAccount => "-gg"
      case false if location == Location.PersonalTaxAccount => "-verify"
      case _ => ""
    }

    Play.configuration.getConfig(s"throttling.locations.${location.name}$suffix").getOrElse(Configuration.empty)
  }

  private def findFallbackFor(configurationForLocation: Configuration, location: LocationType): String = {
    configurationForLocation.getString("fallback").getOrElse(location.name)
  }

  private def findPercentageToThrottleFor(configurationForLocation: Configuration): Option[Int] = {
    configurationForLocation.getString("percentageBeToThrottled").map(_.toInt)
  }

  def findLocationByName(fallbackLocationName: String): Option[LocationType] = {
    Location.locations.get(fallbackLocationName)
  }

  def throttle(location: LocationType, auditContext: TAuditContext)(implicit request: Request[AnyContent]): ThrottlingResult = {
    val routingCookie = request.cookies.get(CookieNames.mdtpRouting)
    val routingCookieValues = routingCookie.map(cookie => RoutingCookieValues(cookie.value))

    val followingPreviouslyRoutedDestination = routingCookieValues.exists(cookie => Location.locations.get(cookie.routedDestination).contains(location) && stickyRoutingEnabled)
    val throttledLocation = followingPreviouslyRoutedDestination match {
      case true =>
        val finalLocation = Location.locations.get(routingCookieValues.get.throttledDestination).get
        auditContext.setThrottlingDetails(ThrottlingAuditContext(throttlingPercentage = None, location != finalLocation, location, throttlingEnabled, followingPreviouslyRoutedDestination))
        finalLocation
      case false =>
        doThrottling(location, auditContext)
    }

    getThrottlingResult(location, throttledLocation)
  }

  case class DoThrottlingResult(location: LocationType, throttlingPercentage: Int)

  def doThrottling(location: LocationType, auditContext: TAuditContext)(implicit request: Request[AnyContent]): LocationType = {
    throttlingEnabled match {
      case false => location
      case true if location != Location.WelcomePTA => {
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
      case true if location == Location.WelcomePTA =>
        val throttlingChanceOption = Play.configuration.getString(s"throttling.locations.${Location.PersonalTaxAccount.name}-gg.percentageBeToThrottled").map(_.toInt)
        val throttlingChance: Int = throttlingChanceOption.getOrElse(0)
        val randomNumber = random.nextInt(100) + 1
        val finalLocation: LocationType = randomNumber match {
          case x if x <= throttlingChance => Location.WelcomeBTA
          case _ => location
        }
        auditContext.setThrottlingDetails(ThrottlingAuditContext(throttlingPercentage = throttlingChanceOption, location != finalLocation, location, throttlingEnabled, followingPreviouslyRoutedDestination = false))
        finalLocation
    }
  }

  def getThrottlingResult(location: LocationType, throttledLocation: LocationType): ThrottlingResult = {
    val throttlingResult: ThrottlingResult = stickyRoutingEnabled match {
      case true =>
        val throttlingResult: Option[ThrottlingResult] = for {
          routedDestination <- cookieValues.get(location)
          throttledDestination <- cookieValues.get(throttledLocation)
          maxAge <- cookieMaxAge.get((routedDestination, throttledDestination)).flatMap(identity)
        } yield ThrottlingResult(throttledLocation = throttledLocation, cookiesToAdd = Seq(Cookie(CookieNames.mdtpRouting, RoutingCookieValues(routedDestination.name, throttledDestination.name).toString, maxAge = Some(maxAge.getMaxAge))))
        throttlingResult.getOrElse(ThrottlingResult(throttledLocation = throttledLocation))
      case false =>
        ThrottlingResult(throttledLocation = throttledLocation, cookiesToRemove = Seq(DiscardingCookie(CookieNames.mdtpRouting)))
    }
    throttlingResult
  }
}

case class ThrottlingResult(throttledLocation: LocationType, cookiesToAdd: Seq[Cookie] = Seq.empty, cookiesToRemove: Seq[DiscardingCookie] = Seq.empty)

object ThrottlingService extends ThrottlingService {

  override val random = Random

}
