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

package controllers

import play.api.Play
import uk.gov.hmrc.play.config.RunMode

object ExternalUrls extends RunMode {

  import play.api.Play.current

  def location(name: String, defaultPath: String): String = {
    val host = Play.configuration.getString(s"$name.host").getOrElse("")
    val path = Play.configuration.getString(s"$name.path").getOrElse(defaultPath)

    host + path
  }

  lazy val companyAuthHost = Play.configuration.getString("company-auth.host").getOrElse("")
  lazy val taxAccountRouterHost = Play.configuration.getString("tax-account-router.host").getOrElse("")

  lazy val signIn = s"$companyAuthHost/account/sign-in?continue=$taxAccountRouterHost/account"
}
