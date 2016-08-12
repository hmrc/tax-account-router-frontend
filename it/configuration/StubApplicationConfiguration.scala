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

trait StubApplicationConfiguration {

  val stubPort = 11111
  val stubHost = "localhost"

  val databaseName = "tar-test"

  val stubbedMicroServices = Seq("auth", "cachable.short-lived-cache", "government-gateway", "sa", "user-details", "platform-analytics")
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
    "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName"
  ) ++ stubbedMicroServices
}