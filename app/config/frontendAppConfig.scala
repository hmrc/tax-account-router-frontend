/*
 * Copyright 2016 HM Revenue & Customs
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
  val assetsPrefix: String
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
  val twoStepVerificationHost: String
  val twoStepVerificationPath: String
  val companyAuthHost: String
  val taxAccountRouterHost: String
}

object FrontendAppConfig extends AppConfig with ServicesConfig {

  private val contactHost = configuration.getString("contact-frontend.host").getOrElse("")
  private val contactFormServiceIdentifier = "MyService"

  override lazy val assetsPrefix = getConfigurationString("assets.url") + getConfigurationString("assets.version")
  override lazy val analyticsToken = getConfigurationString("google-analytics.token")
  override lazy val analyticsHost = getConfigurationString("google-analytics.host")
  override lazy val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  override lazy val twoStepVerificationHost = getConfigurationString("two-step-verification.host")
  override lazy val twoStepVerificationPath = getConfigurationString("two-step-verification.path")
  override lazy val companyAuthHost = getConfigurationStringOption("company-auth.host").getOrElse("")
  override lazy val taxAccountRouterHost = getConfigurationStringOption("tax-account-router.host").getOrElse("")
}
