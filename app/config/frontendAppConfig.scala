/*
 * Copyright 2019 HM Revenue & Customs
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

package config

import play.api.Configuration
import play.api.Play.{configuration, current}
import services.ThrottlingConfig

trait AppConfigHelpers {

  lazy val config: Configuration = configuration

  def getConfigurationStringOption(key: String): Option[String] = config.getString(key)

  def getConfigurationString(key: String): String = config.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  def getConfigurationBoolean(key: String): Boolean = config.getBoolean(key).getOrElse(false)

  def getConfiguration(key: String): Configuration = config.getConfig(key).getOrElse(Configuration.empty)

  def getConfigurationStringSet(key: String): Set[String] = {
    import utils.StringSeparationHelper._
    val values = getConfigurationStringOption(key)
    values.map(_.asCommaSeparatedValues.toSet).getOrElse(Set.empty[String])
  }
}

trait AppConfig extends AppConfigHelpers {

  lazy val analyticsToken: String = getConfigurationString("google-analytics.token")
  lazy val analyticsHost: String = getConfigurationString("google-analytics.host")

  private val contactHost: String = getConfigurationStringOption("contact-frontend.host").getOrElse("")
  private val contactFormServiceIdentifier: String = "tax-account-router-frontend"

  lazy val reportAProblemPartialUrl: String = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  lazy val reportAProblemNonJSUrl: String = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"

  def getLocationConfig(locationName: String, key: String): Option[String] = getConfigurationStringOption(s"locations.$locationName.$key")

  def getThrottlingConfig(locationWithSuffix: String): ThrottlingConfig = {
    val config = getConfiguration(s"throttling.locations.$locationWithSuffix")

    val incorrectConfigurationKey = "percentageBeToThrottled"
    val configurationKey = "percentageToBeThrottled"

    val percentageToBeThrottled = config.getInt(configurationKey)
      .getOrElse(
        config.getInt(incorrectConfigurationKey)
          .getOrElse(0)
      )

    val fallback = config.getString("fallback")

    ThrottlingConfig(percentageToBeThrottled, fallback)
  }

  lazy val businessEnrolments: Set[String] = getConfigurationStringSet("business-enrolments")
  lazy val saEnrolments: Set[String] = getConfigurationStringSet("self-assessment-enrolments")
}

object FrontendAppConfig extends AppConfig {
  override lazy val config = configuration
}
