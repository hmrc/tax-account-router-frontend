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

package model

import config.{AppConfig, FrontendAppConfig}

case class Location(name: String, url: String)

trait Locations {
  def appConfig: AppConfig

  lazy val PersonalTaxAccount = buildLocation("pta")
  lazy val BusinessTaxAccount = buildLocation("bta")
  lazy val TaxAccountRouterHome = buildLocation("tax-account-router")

  lazy val all = List(PersonalTaxAccount, BusinessTaxAccount, TaxAccountRouterHome)

  def verifyConfiguration() = {
    assert(all.nonEmpty)
  }

  def find(name: String): Option[Location] = all.find { case Location(n, _) => n == name }

  private def buildLocation(locationName: String): Location = {

    def getString(key: String): String = {
      appConfig.getLocationConfig(locationName, key)
        .getOrElse(throw new RuntimeException(s"key '$key' not configured for location '$locationName'"))
    }

    val name = getString("name")
    val url = getString("url")

    Location(name, url)
  }
}

object Locations extends Locations {
  val appConfig = FrontendAppConfig
}
