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
import connector.CredentialRole.CredentialRole
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost}
import utils.EnumJson.enumReads

trait UserDetailsConnector {

  val serviceUrl: String

  def http: HttpGet with HttpPost

  def getUserDetails(userDetailsUri: String)(implicit hc: HeaderCarrier) = http.GET[UserDetails](userDetailsUri)
}

object CredentialRole extends Enumeration {
  type CredentialRole = Value
  val User, Unknown = Value
}


object UserDetailsConnector extends UserDetailsConnector with ServicesConfig {
  override lazy val serviceUrl = baseUrl("user-details")
  override lazy val http = WSHttp
}

case class UserDetails(credentialRole: CredentialRole, affinityGroup: String)

object UserDetails {
  implicit val credentialRoleReads: Reads[CredentialRole] = enumReads(CredentialRole).orElse(Reads.pure(CredentialRole.Unknown))
  implicit val reads: Reads[UserDetails] = Json.reads[UserDetails]
}
