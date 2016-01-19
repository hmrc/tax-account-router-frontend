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
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

object GovernmentGatewayConnector extends GovernmentGatewayConnector with ServicesConfig {
  val serviceUrl = baseUrl("government-gateway")
  lazy val http = WSHttp

}

trait GovernmentGatewayConnector {
  val serviceUrl: String

  def http: HttpGet with HttpPost

  def profile(implicit hc: HeaderCarrier): Future[ProfileResponse] = {
    implicit val reads = {
      implicit val reads = Json.reads[Enrolment]
      Json.reads[ProfileResponse]
    }
    http.GET[ProfileResponse](s"$serviceUrl/profile").recover {
      case e: Throwable => {
        Logger.error(s"Unable to retrieve the list of enrolments for the current user", e)
        throw e
      }
    }
  }
}

case class ProfileResponse(affinityGroup: String, enrolments: List[Enrolment])

object ProfileResponse {
  def empty: ProfileResponse = ProfileResponse("Individual", List.empty)
}
case class Enrolment(key: String, identifier: String, state: String)

object AffinityGroupValue {
  val INDIVIDUAL = "Individual"
  val ORGANISATION = "Organisation"
  val AGENT = "Agent"
}

object EnrolmentState {
  val ACTIVATED = "Activated"
  val NOT_YET_ACTIVATED = "NotYetActivated"
}
