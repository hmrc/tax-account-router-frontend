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

import scala.util.Try

case class EnrolmentIdentifier(key: String, value: String)
case class GovernmentGatewayEnrolment(key: String, identifiers: Seq[EnrolmentIdentifier], state: String)

object GovernmentGatewayEnrolment {
  implicit val idFmt = Json.format[EnrolmentIdentifier]
  implicit val fmt = Json.format[GovernmentGatewayEnrolment]
}

case class InternalUserIdentifier(value: String) extends AnyVal

object InternalUserIdentifier {
  implicit val reads: Reads[InternalUserIdentifier] = new Reads[InternalUserIdentifier] {
    override def reads(json: JsValue): JsResult[InternalUserIdentifier] =
      Try((json \ "internalId").as[String])
        .map(id => JsSuccess(InternalUserIdentifier(id)))
        .getOrElse(JsError("Error parsing internal user identifier"))
  }

  implicit def toString(id: InternalUserIdentifier): String = id.value
}

case class CoAFEAuthority(twoFactorAuthOtpId: Option[String], enrolmentsUri: Option[String], userDetailsLink: String, internalUserIdentifier: InternalUserIdentifier)

object FrontendAuthConnector extends FrontendAuthConnector with ServicesConfig {
  val serviceUrl = baseUrl("auth")
  lazy val http = WSHttp
}

trait FrontendAuthConnector extends AuthConnector {

  private case class Authority(twoFactorAuthOtpId: Option[String], enrolmentsUri: Option[String], userDetailsLink: String, idsUri: String)

  private object Authority {
    implicit val reads: Reads[Authority] =
      ((JsPath \ "twoFactorAuthOtpId").readNullable[String] and
        (JsPath \ "enrolments").readNullable[String] and
        (JsPath \ "userDetailsLink").read[String] and
        (JsPath \ "ids").read[String]).apply(Authority.apply _)
  }

  def currentCoAFEAuthority()(implicit hc: HeaderCarrier) =
    http.GET[Authority](s"$serviceUrl/auth/authority")
      .flatMap(authority => http.GET[InternalUserIdentifier](s"$serviceUrl${authority.idsUri}").map(internalUserIdentifier =>
        CoAFEAuthority(authority.twoFactorAuthOtpId, authority.enrolmentsUri, authority.userDetailsLink, internalUserIdentifier)
      ))

  def getEnrolments(enrolmentsUri: String)(implicit hc: HeaderCarrier) = http.GET[Seq[GovernmentGatewayEnrolment]](s"$serviceUrl$enrolmentsUri")
}