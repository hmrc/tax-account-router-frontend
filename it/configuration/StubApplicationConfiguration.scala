package configuration

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

object StubApplicationConfiguration {
  val wiremockPort: Int = 6009
}
trait StubApplicationConfiguration {

  val stubPort = StubApplicationConfiguration.wiremockPort
  val stubHost = "localhost"

  val databaseName = "tar-test"

  val stubbedMicroServices = Seq("auth", "sa", "user-details", "platform-analytics")
    .map(service => Map(
      s"microservice.services.$service.host" -> stubHost,
      s"microservice.services.$service.port" -> stubPort
    )).reduce(_ ++ _)

  val config = Map[String, Any](
    "auditing.consumer.baseUri.host" -> stubHost,
    "auditing.consumer.baseUri.port" -> stubPort,
    "business-tax-account.host" -> s"http://$stubHost:$stubPort",
    "company-auth.host" -> s"http://$stubHost:$stubPort",
    "contact-frontend.host" -> s"http://$stubHost:$stubPort",
    "personal-tax-account.host" -> s"http://$stubHost:$stubPort",
    "two-step-verification.host" -> s"http://$stubHost:$stubPort",
    "two-step-verification-required.host" -> s"http://$stubHost:$stubPort",
    "tax-account-router.host" -> "",
    "throttling.enabled" -> false,
    "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
    "business-enrolments" -> "enr1,enr2",
    "self-assessment-enrolments" -> "enr3,enr4",
    // The request timeout must be less than the value used in the wiremock stubs that use withFixedDelay to simulate network problems.
    "ws.timeout.request" -> 10000,
    "ws.timeout.connection" -> 6000,
    "two-step-verification.enabled" -> true,
    "logger" -> null,
    "locations.two-step-verification-optional.url" -> s"http://localhost:${StubApplicationConfiguration.wiremockPort}/coafe/two-step-verification/register",
    "locations.two-step-verification-optional.queryparams.continue" -> s"http://localhost:${StubApplicationConfiguration.wiremockPort}/business-account",
    "locations.two-step-verification-optional.queryparams.failure" -> s"http://localhost:${StubApplicationConfiguration.wiremockPort}/business-account",
    "locations.two-step-verification-mandatory.url" -> s"http://localhost:${StubApplicationConfiguration.wiremockPort}/coafe/two-step-verification/register",
    "locations.two-step-verification-mandatory.queryparams.continue" -> s"http://localhost:${StubApplicationConfiguration.wiremockPort}/business-account",
    "locations.two-step-verification-mandatory.queryparams.failure" -> "/account",
    "locations.set-up-extra-security.url" -> s"http://localhost:${StubApplicationConfiguration.wiremockPort}/user-delegation/set-up-extra-security",
    "locations.pta.url" -> s"http://localhost:${StubApplicationConfiguration.wiremockPort}/personal-account",
    "locations.bta.url" -> s"http://localhost:${StubApplicationConfiguration.wiremockPort}/business-account"

  ) ++ stubbedMicroServices
}