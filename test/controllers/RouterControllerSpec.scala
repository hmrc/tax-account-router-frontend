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

import com.codahale.metrics.MetricRegistry
import model.Location._
import model._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import services.{RuleService, ThrottlingService, WelcomePageService}
import uk.gov.hmrc.http.cache.client.ShortLivedCache
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class RouterControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication with Eventually {

  "router controller" should {

    "return location provided by rules" in {

      //given
      val expectedLocation: LocationType = BTA
      val rules: List[Rule] = mock[List[Rule]]

      //and
      val userName: String = "userName"

      //and
      implicit val authContext: AuthContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts()), None)
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(("name", userName))
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
      implicit lazy val ruleContext: RuleContext = new RuleContext(userName)
      val auditContext = new AuditContext()

      //and
      val mockRuleService = mock[RuleService]
      when(mockRuleService.fireRules(eqTo(rules), any[AuthContext], any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(Some(expectedLocation))

      //and
      val mockThrottlingService = mock[ThrottlingService]
      when(mockThrottlingService.throttle(expectedLocation, auditContext)).thenReturn(expectedLocation)

      val controller = new TestRouterController(rules = rules, ruleService = mockRuleService, throttlingService = mockThrottlingService)

      //when
      val futureResult: Future[Result] = controller.route

      val result = await(futureResult)

      //then
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe BTA.url

      verify(mockRuleService).fireRules(eqTo(rules), eqTo(authContext), eqTo(ruleContext), eqTo(auditContext))(eqTo(fakeRequest), any[HeaderCarrier])
      verify(mockThrottlingService).throttle(eqTo(expectedLocation), eqTo(auditContext))(eqTo(fakeRequest))
    }

    "return default location" in {

      //given
      val rules: List[Rule] = mock[List[Rule]]
      val expectedLocation: LocationType = BTA

      //and
      val userName: String = "userName"

      //and
      implicit val authContext: AuthContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts()), None)
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(("name", userName))
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
      implicit lazy val ruleContext: RuleContext = new RuleContext(userName)
      val auditContext = new AuditContext()

      //and
      val mockRuleService = mock[RuleService]
      when(mockRuleService.fireRules(eqTo(rules), eqTo(authContext), any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(None)

      //and
      val mockThrottlingService = mock[ThrottlingService]
      when(mockThrottlingService.throttle(expectedLocation, auditContext)).thenReturn(expectedLocation)

      val controller = new TestRouterController(rules = rules, defaultLocation = expectedLocation, ruleService = mockRuleService, throttlingService = mockThrottlingService)

      //when
      val futureResult: Future[Result] = controller.route

      val result = await(futureResult)

      //then
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe expectedLocation.url

      verify(mockRuleService).fireRules(eqTo(rules), eqTo(authContext), eqTo(ruleContext), eqTo(auditContext))(eqTo(fakeRequest), any[HeaderCarrier])
      verify(mockThrottlingService).throttle(eqTo(expectedLocation), eqTo(auditContext))(eqTo(fakeRequest))
    }

    "audit the event before redirecting" in {
      //given
      val rules: List[Rule] = mock[List[Rule]]
      val userName: String = "userName"
      val expectedLocation: LocationType = BTA

      //and
      implicit val authContext: AuthContext = mock[AuthContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(("name", userName))
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
      implicit lazy val ruleContext: RuleContext = new RuleContext(userName)

      val mockAuditContext = mock[TAuditContext]
      val mockAuditEvent = mock[ExtendedDataEvent]
      val auditContextToAuditEventResult: Future[ExtendedDataEvent] = Future(mockAuditEvent)
      when(mockAuditContext.toAuditEvent(any[String])(any[HeaderCarrier], any[AuthContext], any[Request[AnyContent]])).thenReturn(auditContextToAuditEventResult)

      //and
      val mockRuleService = mock[RuleService]
      val fireRulesResult: Future[Option[LocationType]] = Future(None)
      when(mockRuleService.fireRules(eqTo(rules), eqTo(authContext), any[RuleContext], eqTo(mockAuditContext))(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn fireRulesResult

      //and
      val mockThrottlingService = mock[ThrottlingService]
      val expectedThrottledLocation: LocationType = PTA
      when(mockThrottlingService.throttle(expectedLocation, mockAuditContext)).thenReturn(expectedThrottledLocation)

      val mockAuditConnector = mock[AuditConnector]
      val controller = new TestRouterController(
        rules = rules,
        defaultLocation = expectedLocation,
        ruleService = mockRuleService,
        _auditConnector = Some(mockAuditConnector),
        _auditContext = Some(mockAuditContext),
        throttlingService = mockThrottlingService
      )

      //when
      val routeResult: Future[Result] = controller.route

      //then
      await(routeResult)

      //and
      verify(mockAuditContext).toAuditEvent(eqTo(expectedThrottledLocation.url))(any[HeaderCarrier], any[AuthContext], any[Request[AnyContent]])

      eventually {
        val auditEventCaptor: ArgumentCaptor[ExtendedDataEvent] = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
        verify(mockAuditConnector).sendEvent(auditEventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext])
        auditEventCaptor.getValue shouldBe mockAuditEvent
      }

      verify(mockThrottlingService).throttle(eqTo(expectedLocation), eqTo(mockAuditContext))(eqTo(fakeRequest))
    }
  }
}

class TestRouterController(override val rules: List[Rule],
                           override val defaultLocation: LocationType = BTA,
                           override val controllerMetrics: ControllerMetrics = ControllerMetricsStub,
                           override val ruleService: RuleService,
                           override val throttlingService: ThrottlingService,
                           _auditConnector: Option[AuditConnector] = None,
                           _auditContext: Option[TAuditContext] = None) extends RouterController with MockitoSugar {
  override val welcomePageService: WelcomePageService = WelcomePageServiceStub

  override protected def authConnector: AuthConnector = ???

  override val auditConnector: AuditConnector = _auditConnector.getOrElse(mock[AuditConnector])

  override def createAuditContext(): TAuditContext = _auditContext.getOrElse(AuditContext())
}

object WelcomePageServiceStub extends WelcomePageService {
  override def welcomePageSeenKey: String = ???

  override def shortLivedCache: ShortLivedCache = ???

  override def shouldShowWelcomePage(implicit authContext: AuthContext, hc: HeaderCarrier): Future[Boolean] = Future(false)
}

object ControllerMetricsStub extends ControllerMetrics {
  override lazy val registry: MetricRegistry = ???

  override def registerRedirectFor(name: String): Unit = {}
}