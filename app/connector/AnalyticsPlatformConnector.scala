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
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.ws.WSPost
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

case class GaEvent(category: String, action: String, label: String)

object GaEvent {
  implicit val writes: Writes[GaEvent] = Json.writes[GaEvent]
}

case class AnalyticsData(gaClientId: String, events: List[GaEvent])

object AnalyticsData {
  implicit val writes: Writes[AnalyticsData] = Json.writes[AnalyticsData]
}

trait AnalyticsPlatformConnector {
  def serviceUrl: String

  def http: WSPost

  def sendEvents(data: AnalyticsData)(implicit hc: HeaderCarrier): Unit = http.POST[AnalyticsData, HttpResponse](s"$serviceUrl/platform-analytics/event", data, Seq.empty)
}

object AnalyticsPlatformConnector extends AnalyticsPlatformConnector with ServicesConfig {
  override val serviceUrl = baseUrl("platform-analytics")
  override lazy val http = WSHttp
}
