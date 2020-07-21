/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import config.AppConfigHelpers
import play.api.{Configuration, Play}

object ExternalUrls extends AppConfigHelpers {

  override val config: Configuration = Play.current.configuration

  def getUrl(locationName: String): String = {
    val host = getConfigurationStringOption(s"$locationName.host").getOrElse("")
    val path = getConfigurationString(s"$locationName.path")
    host + path
  }

  lazy val companyAuthHost: String = getConfigurationStringOption("company-auth.host").getOrElse("")
  lazy val taxAccountRouterHost: String = getConfigurationStringOption("tax-account-router.host").getOrElse("")

  lazy val signIn = s"$companyAuthHost/gg/sign-in?continue=$taxAccountRouterHost/account"

}
