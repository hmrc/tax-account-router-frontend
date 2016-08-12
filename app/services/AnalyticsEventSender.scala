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
import model.TAuditContext
import play.api.Logger
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.http.HeaderCarrier

trait AnalyticsEventSender {

  private val routingCategory = "routing"
  private val b2svRegistrationCategory = "sos_b2sv_registration_route"

  def analyticsPlatformConnector: AnalyticsPlatformConnector

  def sendEvents(locationName: String, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) = {

    val gaClientId = request.cookies.get("_ga").map(_.value)

    val biz2svRegistrationLabel = if (auditContext.isSentToMandatory2SVRegister) "mandatory" else "optional"

    val biz2svRegistrationEvent = auditContext.twoStepVerificationRuleApplied.fold(List[GaEvent]()) { ruleName =>
      List(GaEvent(b2svRegistrationCategory, s"rule_$ruleName", biz2svRegistrationLabel))
    }

    gaClientId.fold(Logger.warn(s"Couldn't get _ga cookie from request $request")) {
      clientId =>
        val routingEvent = List(GaEvent(routingCategory, locationName, auditContext.ruleApplied))
        val allEvents = routingEvent ++ biz2svRegistrationEvent
        analyticsPlatformConnector.sendEvents(AnalyticsData(clientId, allEvents))
    }
  }
}

object AnalyticsEventSender extends AnalyticsEventSender {
  override lazy val analyticsPlatformConnector = AnalyticsPlatformConnector
}
