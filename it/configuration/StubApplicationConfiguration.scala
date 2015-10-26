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

  val config = Map[String, Any](
    "auditing.consumer.baseUri.host" -> stubHost,
    "auditing.consumer.baseUri.port" -> stubPort,
    "microservice.services.auth.host" -> stubHost,
    "microservice.services.auth.port" -> stubPort,
    "microservice.services.cachable.short-lived-cache.host" -> stubHost,
    "microservice.services.cachable.short-lived-cache.port" -> stubPort,
    "microservice.services.government-gateway.host" -> stubHost,
    "microservice.services.government-gateway.port" -> stubPort,
    "microservice.services.sa.host" -> stubHost,
    "microservice.services.sa.port" -> stubPort,
    "business-tax-account.host" -> s"http://$stubHost:$stubPort",
    "company-auth.host" -> s"http://$stubHost:$stubPort",
    "contact-frontend.host" -> s"http://$stubHost:$stubPort",
    "personal-tax-account.host" -> s"http://$stubHost:$stubPort",
    "tax-account-router.host" -> "",
    "throttling.enabled" -> false
  )
}