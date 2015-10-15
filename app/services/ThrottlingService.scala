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

import model.Location.LocationType
import model.{Location, TAuditContext, ThrottlingAuditContext}
import play.api.Play.current
import play.api.mvc.{AnyContent, Request}
import play.api.{Configuration, Play}

import scala.util.Random

trait ThrottlingService {

  def random: Random

  val throttlingEnabled = Play.configuration.getBoolean("throttling.enabled").getOrElse(false)

  private def findConfigurationFor(location: LocationType)(implicit request: Request[AnyContent]): Configuration = {

    val suffix = request.session.data.contains("token") match {
      case true if location == Location.PTA => "-gg"
      case false if location == Location.PTA => "-verify"
      case _ => ""
    }

    Play.configuration.getConfig(s"throttling.locations.${location.name}$suffix").getOrElse(Configuration.empty)
  }

  private def findFallbackFor(configurationForLocation: Configuration, location: LocationType): String = {
    configurationForLocation.getString("fallback").getOrElse(location.name)
  }

  private def findPercentageToThrottleFor(configurationForLocation: Configuration): Option[Float] = {
    configurationForLocation.getString("percentageBeToThrottled").map(_.toFloat)
  }

  def findLocationByName(fallbackLocationName: String): Option[LocationType] = {
    Location.locations.get(fallbackLocationName)
  }

  def throttle(location: LocationType, auditContext: TAuditContext)(implicit request: Request[AnyContent]): LocationType = {
    var throttlingChanceOption: Option[Float] = None
    val throttledLocation: LocationType = throttlingEnabled match {
      case false => location
      case true => {
        val configurationForLocation: Configuration = findConfigurationFor(location)
        throttlingChanceOption = findPercentageToThrottleFor(configurationForLocation)
        val throttlingChance: Float = throttlingChanceOption.getOrElse(0)
        val randomNumber = random.nextFloat()
        randomNumber match {
          case x if x <= throttlingChance => findLocationByName(findFallbackFor(configurationForLocation, location)).getOrElse(location)
          case _ => location
        }
      }
    }
    val throttlingAuditContext = ThrottlingAuditContext(throttlingPercentage = throttlingChanceOption, location != throttledLocation, location, throttlingEnabled)
    auditContext.setValue(throttlingAuditContext)
    throttledLocation
  }

}

object ThrottlingService extends ThrottlingService {

  override val random = Random

}
