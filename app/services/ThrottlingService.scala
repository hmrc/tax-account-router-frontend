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

import model.Location
import model.Location.LocationType
import play.api.Play
import play.api.Play.current

import scala.util.Random

trait ThrottlingService {

  def random : Random

  val throttlingEnabled = Play.configuration.getBoolean("throttling.enabled").getOrElse(false)

  private def findFallbackFor(location: LocationType): String = {
    Play.configuration.getString(s"throttling.locations.${location.name}.fallback").getOrElse(location.name)
  }

  private def findPercentageToThrottleFor(location: LocationType): Float = {
    Play.configuration.getDouble(s"throttling.locations.${location.name}.percentageBeToThrottled").map(_.toFloat).getOrElse(0)
  }

  def findLocationByName(fallbackLocationName: String): Option[LocationType] = {
    Location.locations.get(fallbackLocationName)
  }

  def throttle(location: LocationType): LocationType = {
    //get config for that location
    throttlingEnabled match {
      case false => location
      case true => {
        val throttlingChance: Float = findPercentageToThrottleFor(location)
        val randomNumber = random.nextFloat()
        randomNumber match {
          case x if x <= throttlingChance => findLocationByName(findFallbackFor(location)).getOrElse(location)
          case _ => location
        }
      }
    }
  }

}

object ThrottlingService extends ThrottlingService{

  override val random = Random

}
