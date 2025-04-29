/*
 * Copyright 2025 HM Revenue & Customs
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

import config.FrontendAppConfig
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.test.FakeRequest
import play.api.test.Helpers.AUTHORIZATION
import support.WireMockConstants.{stubHost, stubPort}

trait SpecCommonHelper extends PlaySpec with GuiceOneServerPerSuite with WireMockMocks with BeforeAndAfterAll with BeforeAndAfterEach {

  lazy val wireMock = new WireMock

  val fakeRequest = FakeRequest().withSession("sessionId" -> "123456789", "authToken" -> "token")

  def buildClient(path: String): WSRequest = {
    app.injector.instanceOf[WSClient].url(s"http://localhost:$port/$path").withHttpHeaders((AUTHORIZATION, "bearer 123"))
  }

  val extraConfig: Map[String, Any] = {
    Map[String, Any](
      "metrics.enabled" -> false,
      "auditing.consumer.baseUri.host" -> stubHost,
      "auditing.consumer.baseUri.port" -> stubPort,
      "microservice.services.auth.host" -> stubHost,
      "microservice.services.auth.port" -> stubPort,
      "enrolment-store.host" -> s"http://$stubHost:$stubPort",
      "new-rules" -> true
    )
  }

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(extraConfig)
    .build()

  lazy val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]

  override protected def beforeAll(): Unit = {
    wireMock.start()
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    wireMock.resetAll()
    standardDataStreamAuditMock()
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    wireMock.stop()
    super.afterAll()
  }
}