/*
 * Copyright 2016 HM Revenue & Customs
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

package connector

import config.WSHttp
import model.CoAFEAuthority
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.http.HeaderCarrier

object FrontendAuthConnector extends FrontendAuthConnector with ServicesConfig {
  val serviceUrl = baseUrl("auth")
  lazy val http = WSHttp
}

trait FrontendAuthConnector extends AuthConnector  {

  def currentCoAFEAuthority()(implicit hc: HeaderCarrier) = http.GET[CoAFEAuthority](s"$serviceUrl/auth/authority")

  def getEnrolments(enrolmentsUri: String)(implicit hc: HeaderCarrier) = http.GET[Seq[GovernmentGatewayEnrolment]](s"$serviceUrl$enrolmentsUri")
}

case class EnrolmentIdentifier(key: String, value: String)
case class GovernmentGatewayEnrolment(key: String, identifiers: Seq[EnrolmentIdentifier], state: String)

object GovernmentGatewayEnrolment {
  implicit val idReads: Reads[EnrolmentIdentifier] = Json.reads[EnrolmentIdentifier]
  implicit val reads: Reads[GovernmentGatewayEnrolment] = Json.reads[GovernmentGatewayEnrolment]
}
