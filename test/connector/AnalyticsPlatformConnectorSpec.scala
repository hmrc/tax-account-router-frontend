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

package connector

import config.FrontendAppConfig
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Writes
import support.UnitSpec
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AnalyticsPlatformConnectorSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  "AnalyticsPlatformConnector" should {

    val scenarios = Table(
      ("scenario", "response"),
      ("response is successful", Future.successful(mock[HttpResponse])),
      ("response status different from 2xx", Future.failed(new RuntimeException()))
    )

    forAll(scenarios) { (scenario: String, _: Future[HttpResponse]) =>
      s"send a GA event to platform-analytics - $scenario" in {

        val data: AnalyticsData = AnalyticsData("gaClientId", List.empty)
        val mockHttp: HttpClient = mock[HttpClient]
        val configuration: FrontendAppConfig = mock[FrontendAppConfig]
        val analyticsPlatformConnector = new AnalyticsPlatformConnector(mockHttp, configuration)

        implicit val hc: HeaderCarrier = HeaderCarrier()

        noException should be thrownBy analyticsPlatformConnector.sendEvents(data)

        verify(mockHttp).POST[AnalyticsData, HttpResponse](
          eqTo(s"null/platform-analytics/event"),
          eqTo(data),
          eqTo(Seq.empty)
        )(
          any[Writes[AnalyticsData]],
          any[HttpReads[HttpResponse]],
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      }
    }
  }

}
