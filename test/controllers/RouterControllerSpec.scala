/*
 * Copyright 2015 HM Revenue & Customs
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

import engine.{Condition, Rule, RuleEngine, When}
import helpers.SpecHelpers
import model.Location._
import model.RoutingReason._
import model._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import services._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.collection.mutable.{Map => mutableMap}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class RouterControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication with Eventually with SpecHelpers {

  case class TestCondition(truth: Boolean) extends Condition {
    override val auditType: Option[RoutingReason] = None

    override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(truth)
  }

  private val trueLocation: LocationType = evaluateUsingPlay(Location.Type("/true", "true"))
  private val falseLocation: LocationType = evaluateUsingPlay(Location.Type("/false", "false"))

  private val cookiesToAdd: Seq[Cookie] = Seq(Cookie(name = "cookieToAdd", value = "cookieToAdd"))
  private val cookiesToDelete: Seq[DiscardingCookie] = Seq(DiscardingCookie(name = "cookieToRemove"))

  val ruleEngineStubReturningSomeLocation = new RuleEngine {
    override val rules: List[Rule] = List(When(TestCondition(true)).thenGoTo(trueLocation))
  }
  val ruleEngineStubReturningNoneLocation = new RuleEngine {
    override val rules: List[Rule] = List(When(TestCondition(false)).thenGoTo(falseLocation))
  }

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

  "router controller" should {

    "return location provided by rules" in {

      //and
      implicit val authContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts()), None)
      implicit lazy val ruleContext = new RuleContext(authContext)
      val auditContext = new AuditContext()

      //and
      val mockThrottlingService = mock[ThrottlingService]
      when(mockThrottlingService.throttle(trueLocation, auditContext)).thenReturn(ThrottlingResult(trueLocation, cookiesToAdd, cookiesToDelete))

      val controller = new TestRouterController(ruleEngine = ruleEngineStubReturningSomeLocation, throttlingService = mockThrottlingService)

      val result = await(controller.route)

      //then
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe "/true"

      verify(mockThrottlingService).throttle(eqTo(trueLocation), eqTo(auditContext))(eqTo(fakeRequest))
    }

    "return default location when location provided by rules is not defined" in {

      //given
      val expectedLocation: LocationType = BusinessTaxAccount

      //and
      implicit val authContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts()), None)
      implicit lazy val ruleContext = new RuleContext(authContext)
      val auditContext = new AuditContext()

      //and
      val mockThrottlingService = mock[ThrottlingService]
      when(mockThrottlingService.throttle(expectedLocation, auditContext)).thenReturn(ThrottlingResult(expectedLocation, cookiesToAdd, cookiesToDelete))

      val controller = new TestRouterController(defaultLocation = expectedLocation, ruleEngine = ruleEngineStubReturningNoneLocation, throttlingService = mockThrottlingService)

      val result = await(controller.route)

      //then
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe expectedLocation.url

      verify(mockThrottlingService).throttle(eqTo(expectedLocation), eqTo(auditContext))(eqTo(fakeRequest))
    }

    "send monitoring events" in {

      //given
      val expectedLocation: LocationType = BusinessTaxAccount

      //and
      implicit val authContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts()), None)
      implicit lazy val ruleContext = new RuleContext(authContext)
      val auditContext = new AuditContext()

      //and
      val mockThrottlingService = mock[ThrottlingService]
      when(mockThrottlingService.throttle(expectedLocation, auditContext)).thenReturn(ThrottlingResult(expectedLocation, cookiesToAdd, cookiesToDelete))

      val mockMetricsMonitoringService = mock[MetricsMonitoringService]

      val controller = new TestRouterController(defaultLocation = expectedLocation, ruleEngine = ruleEngineStubReturningNoneLocation, throttlingService = mockThrottlingService, metricsMonitoringService = mockMetricsMonitoringService)

      val result = await(controller.route)

      //then
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe expectedLocation.url

      verify(mockThrottlingService).throttle(eqTo(expectedLocation), eqTo(auditContext))(eqTo(fakeRequest))

      verify(mockMetricsMonitoringService).sendMonitoringEvents(eqTo(auditContext), eqTo(expectedLocation))(eqTo(authContext), eqTo(fakeRequest), any[HeaderCarrier])
    }

    "audit the event before redirecting" in {
      //given
      implicit val authContext = mock[AuthContext]
      implicit lazy val ruleContext = new RuleContext(authContext)

      val mockAuditContext = mock[TAuditContext]

      val mockAuditEvent = mock[ExtendedDataEvent]
      when(mockAuditEvent.detail).thenReturn(Json.parse("{}"))

      val auditContextToAuditEventResult = Future(mockAuditEvent)
      when(mockAuditContext.toAuditEvent(any[LocationType])(any[HeaderCarrier], any[AuthContext], any[Request[AnyContent]])).thenReturn(auditContextToAuditEventResult)
      when(mockAuditContext.getReasons).thenReturn(mutableMap.empty[String, String])
      when(mockAuditContext.getThrottlingDetails).thenReturn(mutableMap.empty[String, String])

      val mockThrottlingService = mock[ThrottlingService]
      val expectedThrottledLocation: LocationType = PersonalTaxAccount
      when(mockThrottlingService.throttle(trueLocation, mockAuditContext)).thenReturn(ThrottlingResult(expectedThrottledLocation, cookiesToAdd, cookiesToDelete))

      val mockAuditConnector = mock[AuditConnector]
      val controller = new TestRouterController(
        defaultLocation = trueLocation,
        ruleEngine = ruleEngineStubReturningSomeLocation,
        auditConnector = mockAuditConnector,
        auditContext = Some(mockAuditContext),
        throttlingService = mockThrottlingService
      )

      //then
      await(controller.route)

      //and
      verify(mockAuditContext).toAuditEvent(eqTo(expectedThrottledLocation))(any[HeaderCarrier], any[AuthContext], any[Request[AnyContent]])

      eventually {
        val auditEventCaptor: ArgumentCaptor[ExtendedDataEvent] = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
        verify(mockAuditConnector).sendEvent(auditEventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext])
        auditEventCaptor.getValue shouldBe mockAuditEvent
      }

      verify(mockThrottlingService).throttle(eqTo(trueLocation), eqTo(mockAuditContext))(eqTo(fakeRequest))
    }
  }
}

object Mocks extends MockitoSugar {
  def mockThrottlingService = mock[ThrottlingService]

  def mockAuditConnector = mock[AuditConnector]

  def mockMetricsMonitoringService = mock[MetricsMonitoringService]
}

class TestRouterController(override val defaultLocation: LocationType = BusinessTaxAccount,
                           override val metricsMonitoringService: MetricsMonitoringService = Mocks.mockMetricsMonitoringService,
                           override val ruleEngine: RuleEngine,
                           override val throttlingService: ThrottlingService = Mocks.mockThrottlingService,
                           override val auditConnector: AuditConnector = Mocks.mockAuditConnector,
                           auditContext: Option[TAuditContext] = None) extends RouterController {

  override protected def authConnector: AuthConnector = ???

  override def createAuditContext(): TAuditContext = auditContext.getOrElse(AuditContext())
}