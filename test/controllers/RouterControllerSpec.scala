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

package controllers

import cats.data.WriterT
import engine.{AuditInfo, EngineResult, RuleEngine}
import model._
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.LoneElement
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.api.{Application, Logger}
import services._
import support._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}

import scala.concurrent.{ExecutionContext, Future}

class RouterControllerSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite with ScalaFutures with Eventually with LoneElement with LogCapturing {

  val location1 = Location("location1", "/location1")
  val location2 = Location("location2", "/location2")

  private val gaToken = "GA-TOKEN"

  val additionalConfigurations: Map[String, Any] = Map("google-analytics.token" -> gaToken,
    "location1.path" -> location1.url,
    "extended-logging-enabled" -> true
  )

  override lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(additionalConfigurations)
    .build

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
      when(mockAuditInfo.ruleApplied).thenReturn(Some("some rule applied"))
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
        verify(mockAuditConnector).sendExtendedEvent(eqTo(mockAuditEvent))(any[HeaderCarrier], any[ExecutionContext])
        verify(mockAnalyticsEventSender).sendEvents(eqTo(mockAuditInfo), eqTo(location1.name))(eqTo(fakeRequest), any[HeaderCarrier], any[ExecutionContext])
        verify(mockMetricsMonitoringService).sendMonitoringEvents(eqTo(mockAuditInfo), eqTo(location1))(eqTo(fakeRequest), any[HeaderCarrier])
      }
    }

    "send AuditEvent" in new Setup {
      val details: JsObject = Json.obj("reasons" -> Json.obj("a" -> "b"))
      val mockAuditInfo: AuditInfo = mock[AuditInfo]
      val auditEvent: ExtendedDataEvent = ExtendedDataEvent(auditSource = "auditSource", auditType = "auditType", detail = details)
      when(mockAuditInfo.ruleApplied).thenReturn(Some("some rule applied"))
      when(mockAuditInfo.toAuditEvent(eqTo(location2))(any[HeaderCarrier], any[AuthContext], any[Request[AnyContent]])).thenReturn(auditEvent)

      val mockAuditResult: AuditResult = Future.successful(mock[AuditResult])
      when(mockAuditConnector.sendExtendedEvent(eqTo(auditEvent))(any[HeaderCarrier], any[ExecutionContext])).thenReturn(mockAuditResult)

      withCaptureOfLoggingFrom(Logger) { events =>
        val controller: TestRouterController = new TestRouterController()
        controller.sendAuditEvent(ruleContext, mockAuditInfo, location2)(authContext, fakeRequest)
        events.map(_.getMessage) shouldBe List(s"""[AIV-1992] some rule applied, Location = location2, Affinity group = can not found, Enrolments = can not found""", """Routing decision summary: {"a":"b"}""")
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
    implicit lazy val hc = HeaderCarrierConverter.fromHeadersAndSession(fakeRequest.headers)
    implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

    implicit val authContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts()), None, None, None, None)
    implicit lazy val ruleContext = RuleContext(None)(fakeRequest, hc, ec)

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
