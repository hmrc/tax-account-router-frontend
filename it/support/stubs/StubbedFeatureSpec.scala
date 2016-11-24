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

package support.stubs

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import configuration.StubApplicationConfiguration
import org.scalatest._
import org.scalatestplus.play.OneServerPerSuite
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import support.Env
import support.sugar._

trait StubbedFeatureSpec
  extends FeatureSpec
  with GivenWhenThen
  with ShouldMatchers
  with OneServerPerSuite
  with Stubs
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with ImplicitWebDriverSugar
  with NavigationSugar
  with StubSugar
  with OptionValues
  with AssertionSugar
  with StubApplicationConfiguration {

  override lazy val port = 9000
  override lazy val app = FakeApplication(additionalConfiguration = config)

  val wireMockServer: WireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  override def beforeAll() = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll() = {
    wireMockServer.stop()
  }

  sys addShutdownHook {
    webDriver.quit()
  }

  override def beforeEach() = {
    Env.driver.manage().deleteAllCookies()
    WireMock.reset()
  }

}
