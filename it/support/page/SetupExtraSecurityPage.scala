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

object SetupExtraSecurityStubPage extends Stub with StubbedPage {
  override def create() = stubOut(urlEqualTo(SetupExtraSecurityPage.uri), "Set Up Extra Security Page")
}

object SetupExtraSecurityPage extends WebPage {
  private val hostPort = s"http://${Env.stubHost}:${Env.stubPort}"

  val uri = "/user-delegation/set-up-extra-security"
  override val url = s"$hostPort$uri"

  override def assertPageLoaded() = assertPageIs("Set Up Extra Security Page")
}