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

package config

import play.api.Play.{configuration, current}
import uk.gov.hmrc.play.config.ServicesConfig

trait AppConfigHelpers {
  def getConfigurationStringOption(key: String) = configuration.getString(key)

  def getConfigurationString(key: String) = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  def getConfigurationBoolean(key: String) = configuration.getBoolean(key).getOrElse(false)
}

trait AppConfig extends AppConfigHelpers {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String

  def getLocationConfig(locationName: String, key: String): Option[String]
}

object FrontendAppConfig extends AppConfig with ServicesConfig {

  private val contactHost = configuration.getString("contact-frontend.host").getOrElse("")
  private val contactFormServiceIdentifier = "MyService"

  override lazy val analyticsToken = getConfigurationString("google-analytics.token")
  override lazy val analyticsHost = getConfigurationString("google-analytics.host")
  override lazy val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"

  override def getLocationConfig(locationName: String, key: String) = getConfigurationStringOption(s"locations.$locationName.$key")
}
