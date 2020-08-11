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
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import configuration.StubApplicationConfiguration
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import support.Env
import support.sugar._

object StubbedFeatureSpec {
  val fakeApplicationPort: Int = 6010
}
trait StubbedFeatureSpec
  extends FeatureSpec
  with GivenWhenThen
  with Matchers
  with GuiceOneServerPerSuite
  with Stubs
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with ImplicitWebDriverSugar
  with NavigationSugar
  with OptionValues
  with StubApplicationConfiguration {

  override lazy val port: Int = StubbedFeatureSpec.fakeApplicationPort
  override lazy val app: Application = new GuiceApplicationBuilder().configure(config).build()

  val wireMockServer: WireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  override def beforeAll(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
  }

  sys addShutdownHook {
    webDriver.quit()
  }

  override def beforeEach(): Unit = {
    Env.driver.manage().deleteAllCookies()
    WireMock.reset()
    stubAudit()
    stubPlatformAnalytics()
  }

  private def stubAudit() = stubFor(post(urlMatching("/write/audit.*")).willReturn(
    aResponse().withStatus(204)
  ))

  private def stubPlatformAnalytics() = stubFor(post(urlMatching("/platform-analytics/event.*")).willReturn(
    aResponse().withStatus(200)
  ))

}
