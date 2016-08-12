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
import model.AuditContext
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.mvc.Cookie
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class AnalyticsEventSenderSpec extends UnitSpec with MockitoSugar {

  "AnalyticsEventSender" should {

    "send event to GA when not routed to 2sv registration" in new Setup {

      analyticsEventSender.sendEvents(locationName, auditContext)

      verify(mockAnalyticsPlatformConnector).sendEvents(AnalyticsData(gaClientId, List(GaEvent("routing", locationName, auditContext.ruleApplied))))
    }

    "send events to GA when routed to optional 2sv registration" in new Setup {
      val biz2svRuleName = "sa"
      auditContext.setSentToOptional2SVRegister(biz2svRuleName)
      analyticsEventSender.sendEvents(locationName, auditContext)
      verify(mockAnalyticsPlatformConnector).sendEvents(AnalyticsData(gaClientId, List(
        GaEvent("routing", locationName, auditContext.ruleApplied),
        GaEvent("sos_b2sv_registration_route", s"rule_sa", "optional")
      )))
    }

    "send events to GA when routed to mandatory 2sv registration" in new Setup {
      val biz2svRuleName = "sa"
      auditContext.setSentToMandatory2SVRegister(biz2svRuleName)
      analyticsEventSender.sendEvents(locationName, auditContext)
      verify(mockAnalyticsPlatformConnector).sendEvents(AnalyticsData(gaClientId, List(
        GaEvent("routing", locationName, auditContext.ruleApplied),
        GaEvent("sos_b2sv_registration_route", s"rule_sa", "mandatory")
      )))
    }
  }

  sealed trait Setup {
    val gaClientId = "gaClientId"
    implicit val request = FakeRequest().withCookies(Cookie("_ga", gaClientId))
    implicit val hc = HeaderCarrier()
    val mockAnalyticsPlatformConnector = mock[AnalyticsPlatformConnector]
    val analyticsEventSender = new AnalyticsEventSender {
      override val analyticsPlatformConnector = mockAnalyticsPlatformConnector
    }
    val locationName = "some-location"
    val auditContext = AuditContext()
    auditContext.ruleApplied = "some rule"

  }

}
