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

import play.api.libs.json.{Json, Writes}

case class GaEvent(category: String, action: String, label: String, dimensions: List[GaDimension] = Nil)
case class GaDimension(index: Int, value: String)

object GaEvent {
  implicit val gaDimensionWrites: Writes[GaDimension] = Json.writes[GaDimension]
  implicit val writes: Writes[GaEvent] = Json.writes[GaEvent]
}

case class AnalyticsData(gaClientId: String, events: List[GaEvent])

object AnalyticsData {
  implicit val writes: Writes[AnalyticsData] = Json.writes[AnalyticsData]
}

trait AnalyticsPlatformConnector {
  def sendEvent(gaClientId: String, events: List[GaEvent]) = ???
}

object AnalyticsPlatformConnector extends AnalyticsPlatformConnector
