/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EacdConnector @Inject()(httpClient: HttpClient,
                              config: FrontendAppConfig,
                              implicit val ec: ExecutionContext) {

  lazy val enrolmentProxyBase: String = config.enrolmentStore

  def checkGroupEnrolments(groupId: Option[String])(implicit hc: HeaderCarrier): Future[Boolean] = {
    groupId map { id =>
      httpClient.GET[HttpResponse](enrolmentProxyBase + s"/enrolment-store/groups/$id/enrolments").map { res =>
          res.status match {
            case 200 => res.json.as[GroupEnrolments].enrolments.isEmpty
            case _ => true
          }
      }
    } getOrElse Future.successful(true)
  }

}


case class GroupEnrolments(enrolments: Seq[Enrolment])
object GroupEnrolments {
  implicit val reads: Reads[GroupEnrolments] = Json.reads[GroupEnrolments]
}
