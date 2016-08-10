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

import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.libs.json.Writes
import uk.gov.hmrc.play.http.ws.WSPost
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AnalyticsPlatformConnectorSpec extends UnitSpec with MockitoSugar {

  "AnalyticsPlatformConnector" should {

    val scenarios = Table(
      ("scenario", "response"),
      ("response is successful", Future.successful(mock[HttpResponse])),
      ("response status different from 2xx", Future.failed(new RuntimeException()))
    )

    forAll(scenarios) { (scenario: String, response: Future[HttpResponse]) =>
      s"send a GA event to platform-analytics - $scenario" in new Setup {
        when(
          mockHttp.POST[AnalyticsData, HttpResponse]
          (eqTo(s"$aServiceUrl/platform-analytics/event"), eqTo(data), any[Seq[(String, String)]])
          (any[Writes[AnalyticsData]], any[HttpReads[HttpResponse]], any[HeaderCarrier])
        ).thenReturn(response)

        noException should be thrownBy analyticsPlatformConnector.sendEvents(data)

        verify(mockHttp).POST[AnalyticsData, HttpResponse](eqTo(s"$aServiceUrl/platform-analytics/event"), eqTo(data), eqTo(Seq.empty))(any[Writes[AnalyticsData]], any[HttpReads[HttpResponse]], any[HeaderCarrier])
      }
    }
  }

  trait Setup {
    val data = AnalyticsData("gaClientId", List.empty)
    val aServiceUrl = "service-url"
    implicit val hc = HeaderCarrier()

    val mockHttp = mock[WSPost]
    val analyticsPlatformConnector = new AnalyticsPlatformConnector {
      override val serviceUrl = aServiceUrl
      override val http = mockHttp
    }
  }

}
