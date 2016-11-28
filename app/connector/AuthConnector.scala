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
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain.CredentialStrength
import uk.gov.hmrc.play.http.HeaderCarrier

case class EnrolmentIdentifier(key: String, value: String)

import play.api.libs.json.Reads._
import play.api.libs.json._

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

case class TARAuthority(twoFactorAuthOtpId: Option[String], idsUri: String, userDetailsUri: String, enrolmentsUri: Option[String],
                        credentialStrength: CredentialStrength, nino: Option[Nino], saUtr: Option[SaUtr])

object TARAuthority {
  implicit val reads: Reads[TARAuthority] =
    ((__ \ "twoFactorAuthOtpId").readNullable[String] and
      (__ \ "ids").read[String] and
      (__ \ "userDetailsLink").read[String] and
      (__ \ "enrolments").readNullable[String] and
      (__ \ "credentialStrength").read[CredentialStrength] and
      (__ \ "nino").readNullable[Nino] and
      (__ \ "sautr").readNullable[SaUtr]).apply(TARAuthority.apply _)
}


trait FrontendAuthConnector extends AuthConnector {

  def currentTarAuthority(implicit hc: HeaderCarrier) = http.GET[TARAuthority](s"$serviceUrl/auth/authority")

  def tarAuthority(credId: String)(implicit hc: HeaderCarrier) = http.GET[TARAuthority](s"$serviceUrl/auth/gg/$credId")

  def getIds(idsUri: String)(implicit hc: HeaderCarrier) = http.GET[InternalUserIdentifier](s"$serviceUrl$idsUri")

  def getEnrolments(enrolmentsUri: String)(implicit hc: HeaderCarrier) =
    http.GET[Seq[GovernmentGatewayEnrolment]](s"$serviceUrl$enrolmentsUri")
}

object FrontendAuthConnector extends FrontendAuthConnector with ServicesConfig {
  val serviceUrl = baseUrl("auth")
  lazy val http = WSHttp
}