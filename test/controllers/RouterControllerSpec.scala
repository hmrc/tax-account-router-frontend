/*
 * Copyright 2021 HM Revenue & Customs
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
import config.FrontendAppConfig
import engine.{AuditInfo, EngineResult}
import model.{EnrolmentState, _}
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import services._
import support.{LogCapturing, UnitSpec}
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.{ExecutionContext, Future}

class RouterControllerSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite with LogCapturing {

  "router controller" should {

    "return location" in new Setup {

      val mockAuditInfo: AuditInfo = mock[AuditInfo]
      val engineResult: WriterT[Future, AuditInfo, Location] = WriterT(Future.successful((mockAuditInfo, location1)))

      when(mockRuleEngine.getLocation(mockRuleContext)).thenReturn(engineResult)

      when(mockThrottlingService.throttle(any[EngineResult], eqTo(mockRuleContext))(any(),any())).thenReturn(engineResult)

      val mockAuditEvent: ExtendedDataEvent = mock[ExtendedDataEvent]
      when(mockAuditInfo.toAuditEvent(eqTo(location1))(any[HeaderCarrier], any(), any[Request[AnyContent]])).thenReturn(mockAuditEvent)


      val route: Future[Result] = testRouterController.route(expectedEnrolmentsSeq, fakeRequest)

      status(route) shouldBe 303
      Helpers.redirectLocation(route) should contain(location1.url)

      verify(mockThrottlingService).throttle(any[EngineResult], eqTo(mockRuleContext))(any(),any())
      verify(mockAuditConnector).sendExtendedEvent(eqTo(mockAuditEvent))(any[HeaderCarrier], any[ExecutionContext])
      verify(mockAnalyticsEventSender).sendEvents(eqTo(mockAuditInfo), eqTo(location1.name))(eqTo(fakeRequest), any[HeaderCarrier], any[ExecutionContext])
    }

    "send AuditEvent" in new Setup {
      val mockAuditInfo: AuditInfo = mock[AuditInfo]

      val details: JsObject = Json.obj("reasons" -> Json.obj("a" -> "b"))
      val auditEvent: ExtendedDataEvent = ExtendedDataEvent(auditSource = "auditSource", auditType = "auditType", detail = details)
      when(mockAuditInfo.toAuditEvent(eqTo(location2))(any[HeaderCarrier], any(), any[Request[AnyContent]])).thenReturn(auditEvent)

      val mockAuditResult: AuditResult = mock[AuditResult]
      when(mockAuditConnector.sendExtendedEvent(eqTo(auditEvent))(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future.successful(mockAuditResult))

      when(mockFrontendAppConfig.extendedLoggingEnabled).thenReturn(true)

      when(mockAuditInfo.ruleApplied).thenReturn(Some("some rule applied"))
      when(mockRuleContext.affinityGroup(any())).thenReturn(Future.successful("Organisation"))
      when(mockRuleContext.activeEnrolmentKeys(any())).thenReturn(Future.successful(Set.empty[String]))

      withCaptureOfLoggingFrom(Logger) { events =>
        testRouterController.sendAuditEvent(mockRuleContext, mockAuditInfo, location2)(expectedEnrolmentsSeq, fakeRequest)
        events.map(_.getMessage) shouldBe List(s"""[AIV-1992] some rule applied, Location = location2, Affinity group = Success(Organisation), Enrolments = Success(Set())""", """Routing decision summary: {"a":"b"}""")
      }
    }
  }

  trait Setup {

    val location1: Location = Location("location1", "/location1")
    val location2: Location = Location("location2", "/location2")

    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockRuleEngine: TarRules = mock[TarRules]
    val mockAnalyticsEventSender: AnalyticsEventSender = mock[AnalyticsEventSender]
    val mockThrottlingService: ThrottlingService = mock[ThrottlingService]
    val mockRuleContext: RuleContext = mock[RuleContext]
    val mockFrontendAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
    val mockMetricsService: MetricsMonitoringService = mock[MetricsMonitoringService]
    val mockExternalUrls: ExternalUrls = mock[ExternalUrls]

    val messagesControllerComponents: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

    val gaClientId = "gaClientId"

    implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCookies(Cookie("_ga", gaClientId))
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(fakeRequest.headers)
    implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

    val testRouterController = new RouterController(mockAuthConnector, mockAuditConnector,
      mockRuleEngine, mockAnalyticsEventSender, mockThrottlingService, mockRuleContext, mockFrontendAppConfig,
    messagesControllerComponents, mockMetricsService, mockExternalUrls)

    val expectedEnrolmentsSeq: Enrolments =
      Enrolments(
        Set(Enrolment("some-key", Seq(EnrolmentIdentifier("key-1", "value-1")), EnrolmentState.ACTIVATED),
          Enrolment("some-other-key", Seq(EnrolmentIdentifier("key-2", "value-2")), EnrolmentState.NOT_YET_ACTIVATED)
        )
      )
  }
}
