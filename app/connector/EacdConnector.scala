/*
 * Copyright 2022 HM Revenue & Customs
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

import config.FrontendAppConfig
import play.api.Logging
import play.api.http.Status.OK
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class EacdConnector @Inject()(httpClient: HttpClient,
                              config: FrontendAppConfig,
                              implicit val ec: ExecutionContext) extends Logging {

  lazy val enrolmentProxyBase: String = config.enrolmentStore

  def checkGroupEnrolments(groupId: Option[String])(implicit hc: HeaderCarrier): Future[Boolean] = {
    groupId map { id =>
      httpClient.GET[HttpResponse](enrolmentProxyBase + s"/enrolment-store-proxy/enrolment-store/groups/$id/enrolments").map { res =>
          res.status match {
            case OK =>
              Try {
                res.json.as[GroupEnrolments].enrolments.isEmpty
              } match {
                case Success(b) => b
                case _ =>
                  logger.warn(s"Failed to parse ${res.json}")
                  true
              }
            case _ => true
          }
      }
    } getOrElse Future.successful(true)
  }
}

case class GroupEnrolment(service: String)
object Enrolment {
  implicit val reads: Reads[GroupEnrolment] = Json.reads[GroupEnrolment]
}

case class GroupEnrolments(enrolments: Seq[GroupEnrolment])
object GroupEnrolments {
  implicit val enrolmentReads: Reads[GroupEnrolment] = Enrolment.reads
  implicit val reads: Reads[GroupEnrolments] = Json.reads[GroupEnrolments]
}
