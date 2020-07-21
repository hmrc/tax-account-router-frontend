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
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{AnyContentAsEmpty, Cookie}
import play.api.test.FakeRequest
import support.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class AnalyticsEventSenderSpec extends UnitSpec with MockitoSugar {

  "AnalyticsEventSender" should {

    "send event to GA" in {

      val gaClientId = "gaClientId"

      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCookies(Cookie("_ga", gaClientId))
      implicit val hc: HeaderCarrier = HeaderCarrier()
      implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

      val mockAnalyticsPlatformConnector: AnalyticsPlatformConnector = mock[AnalyticsPlatformConnector]
      val analyticsEventSender = new AnalyticsEventSender(mockAnalyticsPlatformConnector)

      val locationName = "some-location"
      val ruleApplied = "some rule"
      val auditInfo: AuditInfo = AuditInfo(
        routingReasons = Map.empty,
        ruleApplied = Some(ruleApplied),
        throttlingInfo = None
      )

      analyticsEventSender.sendEvents(auditInfo, locationName)
      verify(mockAnalyticsPlatformConnector).sendEvents(AnalyticsData(gaClientId, List(GaEvent("routing", locationName, ruleApplied, Nil))))
    }
  }

}
