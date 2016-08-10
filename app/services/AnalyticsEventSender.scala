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

package services

import connector.{AnalyticsData, AnalyticsPlatformConnector, GaEvent}
import play.api.Logger
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.http.HeaderCarrier

trait AnalyticsEventSender {

  private val routingCategory = "routing"

  def analyticsPlatformConnector: AnalyticsPlatformConnector

  private def gaClientId(request: Request[Any]) = request.cookies.get("_ga").map(_.value)

  def sendRoutingEvent(locationName: String, ruleApplied: String)(implicit request: Request[AnyContent], hc: HeaderCarrier) = {
    gaClientId(request).fold(Logger.warn(s"Couldn't get _ga cookie from request $request")) {
      clientId => analyticsPlatformConnector.sendEvents(AnalyticsData(clientId, List(GaEvent(routingCategory, locationName, ruleApplied))))
    }
  }
}

object AnalyticsEventSender extends AnalyticsEventSender {
  override lazy val analyticsPlatformConnector = AnalyticsPlatformConnector
}
