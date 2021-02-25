/*
 * Copyright 2021 HM Revenue & Customs
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

package model

import config.AppConfig
import play.api.{Configuration, Play}

case class Location(name: String, url: String)

trait Locations extends AppConfig {

  lazy override val config: Configuration = Play.current.configuration

  lazy val PersonalTaxAccount: Location = buildLocation("pta")
  lazy val BusinessTaxAccount: Location = buildLocation("bta")
  lazy val TaxAccountRouterHome: Location = buildLocation("tax-account-router")

  lazy val all = List(PersonalTaxAccount, BusinessTaxAccount, TaxAccountRouterHome)

  def verifyConfiguration(): Unit = assert(all.nonEmpty)

  def find(name: String): Option[Location] = all.find { case Location(n, _) => n == name }

  private def buildLocation(locationName: String): Location = {

    def getString(key: String): String = {
      getLocationConfig(locationName, key)
        .getOrElse(throw new RuntimeException(s"key '$key' not configured for location '$locationName'"))
    }

    Location(getString("name"), getString("url"))
  }
}

object Locations extends Locations
