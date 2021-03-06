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

package config

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import services.ThrottlingConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

trait AppConfigHelpers {

  val config: Configuration

  def getConfigurationStringOption(key: String): Option[String] = config.getOptional[String](key)

  def getConfigurationString(key: String): String = config.getOptional[String](key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  def getConfigurationBoolean(key: String): Boolean = config.getOptional[Boolean](key).getOrElse(false)

  def getConfiguration(key: String): Configuration = config.getOptional[Configuration](key).getOrElse(Configuration.empty)

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

    val percentageToBeThrottled = config.getOptional[Int](configurationKey)
      .getOrElse(
        config.getOptional[Int](incorrectConfigurationKey)
          .getOrElse(0)
      )

    val fallback = config.getOptional[String]("fallback")

    ThrottlingConfig(percentageToBeThrottled, fallback)
  }

  lazy val businessEnrolments: Set[String] = getConfigurationStringSet("business-enrolments")
  lazy val saEnrolments: Set[String] = getConfigurationStringSet("self-assessment-enrolments")

  lazy val financiallySensitiveEnrolments: Set[String] = getConfigurationStringSet("financially-sensitive-enrolments")
}

@Singleton
class FrontendAppConfig @Inject()(val runModeConfiguration: Configuration,
                                  val servicesConfig: ServicesConfig) extends AppConfig {

  lazy override val config: Configuration = runModeConfiguration

  lazy val extendedLoggingEnabled: Boolean = runModeConfiguration
    .getOptional[Boolean]("extended-logging-enabled").getOrElse(false)

  lazy val throttlingEnabled: Boolean = runModeConfiguration
    .getOptional[Boolean]("throttling.enabled").getOrElse(false)

  lazy val paServiceUrl: String = servicesConfig.baseUrl("platform-analytics")
  lazy val saServiceUrl: String = servicesConfig.baseUrl("sa")

  lazy val enrolmentStore: String = servicesConfig.baseUrl("enrolment-store")
  lazy val bta: String = servicesConfig.getString("business-tax-account.host") + servicesConfig.getString("business-tax-account.path")
  lazy val pta: String = servicesConfig.getString("personal-tax-account.host") + servicesConfig.getString("personal-tax-account.path")
  lazy val agents: String = servicesConfig.getString("agents.host") + servicesConfig.getString("agents.path")
  lazy val agentsClassic: String = servicesConfig.getString("agents-classic.host") + servicesConfig.getString("agents-classic.path")

  lazy val useNewRules: Boolean = servicesConfig.getBoolean("new-rules")
}
