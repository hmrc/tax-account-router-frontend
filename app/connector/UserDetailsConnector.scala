/*
 * Copyright 2019 HM Revenue & Customs
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

import config.{HttpClient, WSHttpClient}
import play.api.{Configuration, Play}
import play.api.Mode.Mode
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.ExecutionContext

object AffinityGroupValue {
  val INDIVIDUAL = "Individual"
  val ORGANISATION = "Organisation"
  val AGENT = "Agent"
}

trait UserDetailsConnector {

  val serviceUrl: String

  def httpClient: HttpClient

  def getUserDetails(userDetailsUri: String)(implicit hc: HeaderCarrier, ec: ExecutionContext) = httpClient.GET[UserDetails](userDetailsUri)
}

case class CredentialRole(value: String) extends AnyVal {
  def isAdmin = value == "User"
}

object UserDetailsConnector extends UserDetailsConnector with ServicesConfig {
  override lazy val serviceUrl = baseUrl("user-details")
  override lazy val httpClient = WSHttpClient

  override protected def mode: Mode = Play.current.mode

  override protected def runModeConfiguration: Configuration = Play.current.configuration
}

case class UserDetails(credentialRole: Option[CredentialRole], affinityGroup: String) {
  def isAdmin = credentialRole.fold(false)(_.isAdmin)
}

object UserDetails {
  implicit val credentialRoleReads: Reads[CredentialRole] = new Reads[CredentialRole] {
    override def reads(json: JsValue): JsResult[CredentialRole] = JsSuccess(CredentialRole(json.as[String]))
  }
  implicit val reads: Reads[UserDetails] = Json.reads[UserDetails]
}
