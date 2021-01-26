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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, TestSuite}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting
import play.api.{Application, Environment, Mode}
import support.TARIntegrationTest.{stubHost, stubPort}

trait TARIntegrationTest
    extends Eventually
    with IntegrationPatience
    with Injecting
    with GuiceOneServerPerSuite
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  this: TestSuite =>

  override implicit final lazy val app: Application = GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure(baseConfig ++ config)
    .build()

  protected def config: Map[String, Any] = Map.empty

  val databaseName = "tar-test"

  private final def baseConfig: Map[String, Any] =
    Map(
      "auditing.enabled" -> "false",
      "play.filters.csrf.header.bypassHeaders.Csrf-Token" -> "nocheck",
      "messages-feature-switch" -> "false",
      "enrolment-store-proxy.host"-> "http://localhost:6002",

      "auditing.consumer.baseUri.host" -> stubHost,
      "auditing.consumer.baseUri.port" -> stubPort,
      "business-tax-account.host" -> "http://localhost:6002",
      "company-auth.host" -> "http://localhost:6002",
      "contact-frontend.host" -> "http://localhost:6002",
      "personal-tax-account.host" -> "http://localhost:6002",
      "two-step-verification.host" -> "http://localhost:6002",
      "two-step-verification-required.host" -> "http://localhost:6002",

  "tax-account-router.host" -> "",
  "throttling.enabled" -> false,
  "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
  "business-enrolments" -> "enr1,enr2",
  "self-assessment-enrolments" -> "enr3,enr4",
  // The request timeout must be less than the value used in the wiremock support.support.stubs that use withFixedDelay to simulate network problems.
  "ws.timeout.request" -> 10000,
  "ws.timeout.connection" -> 6000,
  "two-step-verification.enabled" -> true,
  "logger" -> null,

      "locations.two-step-verification-optional.url" -> s"http://localhost:${stubPort}/coafe/two-step-verification/register",
      "locations.two-step-verification-optional.queryparams.continue" -> s"http://localhost:${stubPort}/business-account",
      "locations.two-step-verification-optional.queryparams.failure" -> s"http://localhost:${stubPort}/business-account",
      "locations.two-step-verification-mandatory.url" -> s"http://localhost:${stubPort}/coafe/two-step-verification/register",
      "locations.two-step-verification-mandatory.queryparams.continue" -> s"http://localhost:${stubPort}/business-account",
      "locations.two-step-verification-mandatory.queryparams.failure" -> "/account",
      "locations.set-up-extra-security.url" -> s"http://localhost:${stubPort}/user-delegation/set-up-extra-security",
      "locations.pta.url" -> s"http://localhost:${stubPort}/personal-account",
      "locations.bta.url" -> s"http://localhost:${stubPort}/business-account"


    ) ++ microservices.flatMap { microserviceName =>
      val key: String = s"microservice.services.$microserviceName"
      Map(s"$key.host" -> stubHost, s"$key.port" -> stubPort)
    }

  private final def microservices: Seq[String] = Seq(
    "sa",
    "auth",
    "cachable",
    "user-details",
    "platform-analytics"
  )

  lazy val wmConfig: WireMockConfiguration = wireMockConfig() port stubPort

  lazy val wireMockServer: WireMockServer = new WireMockServer(wmConfig)

  override def beforeAll(): Unit = {
    super.beforeAll()
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll(): Unit = {
    super.beforeAll()
    wireMockServer.stop()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    WireMock.reset()
  }

}

object TARIntegrationTest {
  val stubHost = "localhost"
  val stubPort = 6002
}
