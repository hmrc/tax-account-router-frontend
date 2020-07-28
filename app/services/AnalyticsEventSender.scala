/*
 * Copyright 2020 HM Revenue & Customs
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
import engine.AuditInfo
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

@Singleton
class AnalyticsEventSender @Inject()(analyticsPlatformConnector: AnalyticsPlatformConnector) {

  private val routingCategory = "routing"

  def sendEvents(auditInfo: AuditInfo, locationName: String)(implicit request: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val gaClientId = request.cookies.get("_ga").map(_.value)
    gaClientId.fold {
      Logger.info(s"No _ga cookie in request $request, skipping sending analytics event")
    } {
      clientId =>
        val routingEvent = List(GaEvent(routingCategory, locationName, auditInfo.ruleApplied.getOrElse(""), Nil))
        analyticsPlatformConnector.sendEvents(AnalyticsData(clientId, routingEvent))
    }
  }

}
