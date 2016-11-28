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
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

case class EnrolmentIdentifier(key: String, value: String)
case class GovernmentGatewayEnrolment(key: String, identifiers: Seq[EnrolmentIdentifier], state: String)

object GovernmentGatewayEnrolment {
  implicit val idFmt = Json.format[EnrolmentIdentifier]
  implicit val fmt = Json.format[GovernmentGatewayEnrolment]
}

case class InternalUserIdentifier(value: String) extends AnyVal

object InternalUserIdentifier {
  implicit val reads: Reads[InternalUserIdentifier] = (__ \ "internalId").read[String].map(InternalUserIdentifier(_))

  implicit def convertToString(id: InternalUserIdentifier): String = id.value
}

case class CoAFEAuthority(twoFactorAuthOtpId: Option[String], enrolmentsUri: Option[String], userDetailsLink: Option[String], idsUri: Option[String])

object CoAFEAuthority {
  implicit val reads: Reads[CoAFEAuthority] =
    ((__ \ "twoFactorAuthOtpId").readNullable[String] and
      (__ \ "enrolments").readNullable[String] and
      (__ \ "userDetailsLink").readNullable[String] and
      (__ \ "ids").readNullable[String])(apply _)
}

object FrontendAuthConnector extends FrontendAuthConnector with ServicesConfig {
  val serviceUrl = baseUrl("auth")
  lazy val http = WSHttp
}

trait FrontendAuthConnector extends AuthConnector {

  def getIds(idsUri: String)(implicit hc: HeaderCarrier) = http.GET[InternalUserIdentifier](s"$serviceUrl$idsUri")

  def currentCoAFEAuthority()(implicit hc: HeaderCarrier) = http.GET[CoAFEAuthority](s"$serviceUrl/auth/authority")

  def getEnrolments(enrolmentsUri: String)(implicit hc: HeaderCarrier) = http.GET[Seq[GovernmentGatewayEnrolment]](s"$serviceUrl$enrolmentsUri")
}