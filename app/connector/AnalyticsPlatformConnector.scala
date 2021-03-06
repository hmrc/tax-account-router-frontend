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
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext

case class Dimension(index: String, value: String)

case class GaEvent(category: String, action: String, label: String, dimensions: List[Dimension])

object GaEvent {
  implicit val dimensionWrites: Writes[Dimension] = Json.writes[Dimension]
  implicit val eventWrites: Writes[GaEvent] = Json.writes[GaEvent]
}

case class AnalyticsData(gaClientId: String, events: List[GaEvent])

object AnalyticsData {
  implicit val writes: Writes[AnalyticsData] = Json.writes[AnalyticsData]
}

@Singleton
class AnalyticsPlatformConnector @Inject()(httpClient: HttpClient,
                                           appConfig: FrontendAppConfig){
  val serviceUrl: String = appConfig.paServiceUrl

  def sendEvents(data: AnalyticsData)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    httpClient.POST[AnalyticsData, HttpResponse](s"$serviceUrl/platform-analytics/event", data, Seq.empty)

}
