/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

trait SelfAssessmentConnector {

  val serviceUrl: String

  def httpClient: HttpClient

  def lastReturn(utr: String)(implicit hc: HeaderCarrier): Future[SaReturn] = {

    httpClient.GET[SaReturn](s"$serviceUrl/sa/individual/$utr/return/last").recover {
      case _: NotFoundException => SaReturn.empty
      case e: Throwable =>
        Logger.warn(s"Unable to retrieve last sa return details for user with utr $utr", e)
        throw e
    }
  }

}

object SelfAssessmentConnector extends SelfAssessmentConnector with ServicesConfig {

  override val serviceUrl: String = baseUrl("sa")

  lazy val httpClient = WSHttpClient
}

case class SaReturn(supplementarySchedules: List[String] = List.empty, previousReturns: Boolean = true) {
  private val selfEmploymentKey = "self_employment"
  private val partnershipKey = "partnership"

  def selfEmployment = supplementarySchedules.contains(selfEmploymentKey)

  def partnership = supplementarySchedules.contains(partnershipKey)
}

object SaReturn {
  def empty: SaReturn = SaReturn(List.empty, previousReturns = false)

  implicit val reads: Reads[SaReturn] = (__ \ "supplementarySchedules").read[List[String]].map(SaReturn(_))
}
