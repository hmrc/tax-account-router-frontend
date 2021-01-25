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

package support

import org.joda.time.DateTime
import play.api.Application
import play.api.http.HeaderNames
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.test.Helpers._
import support.SessionCookieBaker.cookieValue

private[support] sealed abstract class TestClient(baseContext: String) {

  def wsClient(implicit app: Application): WSClient = app.injector.instanceOf[WSClient]

  private def buildFullPath(path: String)(implicit app: Application): WSRequest =
    wsClient.url(s"http://localhost:$testServerPort$baseContext$path").withFollowRedirects(follow = false)

  def get(url: String)(implicit app: Application): WSResponse =
    await(buildFullPath(url).get())

  def get(url: String, cookies: Map[String, String])(implicit app: Application): WSResponse =
    await(buildFullPath(url).withHttpHeaders(HeaderNames.COOKIE -> cookieValue(cookies, None)).get())

  def get(url: String, configuredDateTime: DateTime)(implicit app: Application): WSResponse =
    get(url, configuredDateTime, cookies = Map.empty)

  def get(url: String, configuredDateTime: DateTime, cookies: Map[String, String])(
      implicit app: Application
  ): WSResponse =
    await(buildFullPath(url).withHttpHeaders(HeaderNames.COOKIE -> cookieValue(cookies, Some(configuredDateTime))).get())

}

object TARIntegrationTestClient {

  object TARRoutes extends TestClient("/account")

}
