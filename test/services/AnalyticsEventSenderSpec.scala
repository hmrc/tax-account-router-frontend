/*
 * Copyright 2017 HM Revenue & Customs
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
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.mvc.Cookie
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class AnalyticsEventSenderSpec extends UnitSpec with MockitoSugar {

  "AnalyticsEventSender" should {
    "send event to GA" in new Setup {
      analyticsEventSender.sendEvents(locationName, auditInfo)
      verify(mockAnalyticsPlatformConnector).sendEvents(AnalyticsData(gaClientId, List(GaEvent("routing", locationName, ruleApplied, Nil))))
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
    val ruleApplied = "some rule"
    val auditInfo = AuditInfo(
      routingReasons = Map.empty,
      ruleApplied = Some(ruleApplied),
      throttlingInfo = None
    )
  }
}
