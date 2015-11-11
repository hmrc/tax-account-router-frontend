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

package connector

import config.WSHttp
import play.api.libs.json._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, NotFoundException}

import scala.concurrent.Future

trait SelfAssessmentConnector {

  val serviceUrl: String

  def http: HttpGet

  def lastReturn(utr: String)(implicit hc: HeaderCarrier): Future[SaReturn] = {

    implicit val reads: Reads[SaReturn] = (__ \ "supplementarySchedules").read[List[String]].map(SaReturn(_))

    http.GET[SaReturn](s"$serviceUrl/sa/individual/$utr/last-return").recover {
      case e: NotFoundException => SaReturn.empty
    }
  }

}

object SelfAssessmentConnector extends SelfAssessmentConnector with ServicesConfig {

  override val serviceUrl: String = baseUrl("sa")

  lazy val http = WSHttp
}

case class SaReturn(supplementarySchedules: List[String] = List.empty, previousReturns: Boolean = true) {
  private val selfEmploymentKey = "self_employment"
  private val partnershipKey = "partnership"

  def selfEmployment = supplementarySchedules.contains(selfEmploymentKey)

  def partnership = supplementarySchedules.contains(partnershipKey)
}

object SaReturn {
  def empty: SaReturn = SaReturn(List.empty, previousReturns = false)
}