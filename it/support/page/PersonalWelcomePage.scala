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

package support.page

import com.github.tomakehurst.wiremock.client.WireMock._
import support.Env
import support.stubs.{Stub, StubbedPage}

object PersonalWelcomeStubPage extends Stub with StubbedPage {
  override def create() = {
    stubOut(urlMatching("/account/welcome-personal"), "PTA welcome Page")
  }
}

object PersonalWelcomePage extends WebPage {
  override val url: String = s"${Env.host}/account/welcome-personal"

  override def isCurrentPage: Boolean = {
    bodyText contains "File your Self Assessment in your new personal tax account"
  }

  def clickContinue() = {
    click on id("continue-button")
  }
}
