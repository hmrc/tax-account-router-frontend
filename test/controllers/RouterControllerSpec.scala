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

package controllers

import cats.data.WriterT
import engine.{AuditInfo, EngineResult, RuleEngine}
import model.{Location, _}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.LoneElement
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeRequest, Helpers}
import services._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class RouterControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication with ScalaFutures with Eventually with LoneElement with LogCapturing {

  val location1 = Location("location1", "/location1")
  val location2 = Location("location2", "/location2")

  private val gaToken = "GA-TOKEN"
  override lazy val fakeApplication = FakeApplication(
    additionalConfiguration = Map("google-analytics.token" -> gaToken, "location1.path" -> location1.url)
  )

  def withExpectedLogMessages(expectedLogMessages: List[String])(block: => Unit) {

    withCaptureOfLoggingFrom(Logger) { logEvents =>
      block

      eventually {
        val logMessages = logEvents.map(_.getMessage)
        expectedLogMessages.foreach { expectedLogMessage =>
          logMessages should contain(expectedLogMessage)
        }
      }
    }
  }

  "router controller" should {

    "return location" in new Setup {
      val mockAuditInfo = mock[AuditInfo]
      val engineResult = WriterT(Future.successful((mockAuditInfo, location1)))

      when(mockThrottlingService.throttle(any[EngineResult], eqTo(ruleContext))).thenReturn(engineResult)

      val mockAuditEvent = mock[ExtendedDataEvent]
      when(mockAuditInfo.toAuditEvent(eqTo(location1))(any[HeaderCarrier], any[AuthContext], any[Request[AnyContent]])).thenReturn(mockAuditEvent)

      val detail = Json.obj(
        "reasons" -> Json.obj(
          "a" -> "b",
          "c" -> "d"
        )
      )

      when(mockAuditEvent.detail).thenReturn(detail)

      when(mockRuleEngine.getLocation(ruleContext)).thenReturn(engineResult)

      val origin = "some-origin"
      val controller = new TestRouterController()

      withExpectedLogMessages(List(
        """Routing decision summary: {"a":"b","c":"d"}""",
        "Routing to: location1"
      )) {
        //when
        val route = controller.route(authContext, fakeRequest)

        //then
        status(route) shouldBe 303
        Helpers.redirectLocation(route) should contain(location1.url)

        verify(mockThrottlingService).throttle(any[EngineResult], eqTo(ruleContext))
      }

      eventually {
        verify(mockAuditConnector).sendEvent(eqTo(mockAuditEvent))(any[HeaderCarrier], any[ExecutionContext])
        verify(mockAnalyticsEventSender).sendEvents(eqTo(location1.name), eqTo(mockAuditInfo))(eqTo(fakeRequest), any[HeaderCarrier])
        verify(mockMetricsMonitoringService).sendMonitoringEvents(eqTo(mockAuditInfo), eqTo(location1))(eqTo(fakeRequest), any[HeaderCarrier])
      }
    }
  }

  trait Setup {

    val mockAnalyticsEventSender = mock[AnalyticsEventSender]
    val mockThrottlingService = mock[ThrottlingService]
    val mockAuditConnector = mock[AuditConnector]
    val mockMetricsMonitoringService = mock[MetricsMonitoringService]
    val mockAuthConnector = mock[AuthConnector]
    val mockRuleEngine = mock[RuleEngine]

    val gaClientId = "gaClientId"

    implicit val fakeRequest = FakeRequest().withCookies(Cookie("_ga", gaClientId))
    implicit lazy val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

    implicit val authContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts()), None, None, None, None)
    implicit lazy val ruleContext = RuleContext(None)(fakeRequest, hc)

    class TestRouterController(override val metricsMonitoringService: MetricsMonitoringService = mockMetricsMonitoringService,
                               override val ruleEngine: RuleEngine = mockRuleEngine,
                               override val throttlingService: ThrottlingService = mockThrottlingService,
                               override val auditConnector: AuditConnector = mockAuditConnector,
                               override val analyticsEventSender: AnalyticsEventSender = mockAnalyticsEventSender
                              ) extends RouterController {

      override protected def authConnector = ???
    }
  }
}
