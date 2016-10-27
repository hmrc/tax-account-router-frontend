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

package model

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

case class CoAFEAuthority(twoFactorAuthOtpId: Option[String], enrolmentsUri: String, userDetailsLink: String, credentialId: String)

object CoAFEAuthority {
  implicit val reads =
    ((JsPath \ "twoFactorAuthOtpId").readNullable[String] and
      (JsPath \ "enrolments").read[String] and
      (JsPath \ "userDetailsLink").read[String] and
      (JsPath \ "credentials" \ "gatewayId").read[String]).apply(CoAFEAuthority.apply _)
}
