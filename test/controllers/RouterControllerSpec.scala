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
import model.Locations._
import model.RoutingReason._
import model.{Location, _}
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeRequest}
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

  private val trueLocation: Location = evaluateUsingPlay(Location("true", "/true"))
  private val falseLocation: Location = evaluateUsingPlay(Location("false", "/false"))

  val ruleEngineStubReturningSomeLocation = new RuleEngine {
    override val rules: List[Rule] = List(When(TestCondition(true)).thenGoTo(trueLocation))
  }
  val ruleEngineStubReturningNoneLocation = new RuleEngine {
    override val rules: List[Rule] = List(When(TestCondition(false)).thenGoTo(falseLocation))
  }

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

  private val gaToken = "GA-TOKEN"
  override lazy val fakeApplication: FakeApplication = new FakeApplication(additionalConfiguration = Map("google-analytics.token" -> gaToken))

  "router controller" should {

    "return location provided by rules" in {

      //and
      implicit val authContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts()), None)
      implicit lazy val ruleContext = RuleContext(authContext)
      val auditContext = AuditContext()

      //and
      val mockThrottlingService = mock[ThrottlingService]
      when(mockThrottlingService.throttle(eqTo(trueLocation), eqTo(auditContext))(eqTo(fakeRequest), eqTo(authContext), any[ExecutionContext])).thenReturn(trueLocation)

      val controller = new TestRouterController(ruleEngine = ruleEngineStubReturningSomeLocation, throttlingService = mockThrottlingService)

      //when
      val route: Future[Result] = controller.route

      //then
      status(route) shouldBe 200
      val body: String = contentAsString(route)

      //and
      val page = Jsoup.parse(body)

      page.select("p#non-js-only a").attr("href") shouldBe trueLocation.url

      //and
      body.contains(s"ga('create', '$gaToken', 'auto');")  shouldBe true
      body.contains(s"ga('send', 'event', 'routing', '${trueLocation.name}', 'none', {")  shouldBe true

      verify(mockThrottlingService).throttle(eqTo(trueLocation), eqTo(auditContext))(eqTo(fakeRequest), eqTo(authContext), any[ExecutionContext])
    }

    "return default location when location provided by rules is not defined" in {

      //given
      val expectedLocation: Location = BusinessTaxAccount

      //and
      implicit val authContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts()), None)
      implicit lazy val ruleContext = new RuleContext(authContext)
      val auditContext = new AuditContext()

      //and
      val mockThrottlingService = mock[ThrottlingService]
      when(mockThrottlingService.throttle(eqTo(expectedLocation), eqTo(auditContext))(eqTo(fakeRequest), eqTo(authContext), any[ExecutionContext])).thenReturn(expectedLocation)

      val controller = new TestRouterController(defaultLocation = expectedLocation, ruleEngine = ruleEngineStubReturningNoneLocation, throttlingService = mockThrottlingService)

      //when
      val route: Future[Result] = controller.route

      //then
      status(route) shouldBe 200
      val body: String = contentAsString(route)

      //and
      val page = Jsoup.parse(body)

      page.select("p#non-js-only a").attr("href") shouldBe BusinessTaxAccount.url

      //and
      body.contains(s"ga('create', '$gaToken', 'auto');")  shouldBe true
      body.contains(s"ga('send', 'event', 'routing', '${BusinessTaxAccount.name}', '', {")  shouldBe true

      verify(mockThrottlingService).throttle(eqTo(expectedLocation), eqTo(auditContext))(eqTo(fakeRequest), eqTo(authContext), any[ExecutionContext])
    }

    "send monitoring events" in {

      //given
      val expectedLocation: Location = BusinessTaxAccount

      //and
      implicit val authContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts()), None)
      implicit lazy val ruleContext = new RuleContext(authContext)
      val auditContext = new AuditContext()

      //and
      val mockThrottlingService = mock[ThrottlingService]
      when(mockThrottlingService.throttle(eqTo(expectedLocation), eqTo(auditContext))(eqTo(fakeRequest), eqTo(authContext), any[ExecutionContext])).thenReturn(expectedLocation)

      val mockMetricsMonitoringService = mock[MetricsMonitoringService]

      val controller = new TestRouterController(defaultLocation = expectedLocation, ruleEngine = ruleEngineStubReturningNoneLocation, throttlingService = mockThrottlingService, metricsMonitoringService = mockMetricsMonitoringService)

      //when
      val route: Future[Result] = controller.route

      //then
      status(route) shouldBe 200
      val body: String = contentAsString(route)

      //and
      val page = Jsoup.parse(body)

      page.select("p#non-js-only a").attr("href") shouldBe BusinessTaxAccount.url

      //and
      body.contains(s"ga('create', '$gaToken', 'auto');")  shouldBe true
      body.contains(s"ga('send', 'event', 'routing', '${BusinessTaxAccount.name}', '', {")  shouldBe true

      verify(mockThrottlingService).throttle(eqTo(expectedLocation), eqTo(auditContext))(eqTo(fakeRequest), eqTo(authContext), any[ExecutionContext])

      verify(mockMetricsMonitoringService).sendMonitoringEvents(eqTo(auditContext), eqTo(expectedLocation))(eqTo(authContext), eqTo(fakeRequest), any[HeaderCarrier])
    }

    "audit the event before redirecting" in {
      //given
      implicit val authContext = mock[AuthContext]
      implicit lazy val ruleContext = new RuleContext(authContext)
      val ruleApplied = "rule-applied"

      val mockAuditContext = mock[TAuditContext]

      val mockAuditEvent = mock[ExtendedDataEvent]
      when(mockAuditEvent.detail).thenReturn(Json.parse("{}"))
      when(mockAuditContext.ruleApplied).thenReturn(ruleApplied)

      val auditContextToAuditEventResult = Future(mockAuditEvent)
      when(mockAuditContext.toAuditEvent(any[Location])(any[HeaderCarrier], any[AuthContext], any[Request[AnyContent]])).thenReturn(auditContextToAuditEventResult)
      when(mockAuditContext.getReasons).thenReturn(mutableMap.empty[String, String])
      when(mockAuditContext.getThrottlingDetails).thenReturn(mutableMap.empty[String, String])

      val mockThrottlingService = mock[ThrottlingService]
      val expectedThrottledLocation: Location = PersonalTaxAccount
      when(mockThrottlingService.throttle(eqTo(trueLocation), eqTo(mockAuditContext))(eqTo(fakeRequest), eqTo(authContext), any[ExecutionContext])).thenReturn(expectedThrottledLocation)

      val mockAuditConnector = mock[AuditConnector]
      val controller = new TestRouterController(
        defaultLocation = trueLocation,
        ruleEngine = ruleEngineStubReturningSomeLocation,
        auditConnector = mockAuditConnector,
        auditContext = Some(mockAuditContext),
        throttlingService = mockThrottlingService
      )

      //then
      val result = await(controller.route)
      val body = contentAsString(result)

      //and
      body.contains(s"ga('create', '$gaToken', 'auto');")  shouldBe true
      body.contains(s"ga('send', 'event', 'routing', '${PersonalTaxAccount.name}', '$ruleApplied', {")  shouldBe true

      //and
      verify(mockAuditContext).toAuditEvent(eqTo(expectedThrottledLocation))(any[HeaderCarrier], any[AuthContext], any[Request[AnyContent]])

      eventually {
        val auditEventCaptor: ArgumentCaptor[ExtendedDataEvent] = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
        verify(mockAuditConnector).sendEvent(auditEventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext])
        auditEventCaptor.getValue shouldBe mockAuditEvent
      }

      verify(mockThrottlingService).throttle(eqTo(trueLocation), eqTo(mockAuditContext))(eqTo(fakeRequest), eqTo(authContext), any[ExecutionContext])
    }
  }
}

object Mocks extends MockitoSugar {
  def mockThrottlingService = mock[ThrottlingService]

  def mockAuditConnector = mock[AuditConnector]

  def mockMetricsMonitoringService = mock[MetricsMonitoringService]
}

class TestRouterController(override val defaultLocation: Location = BusinessTaxAccount,
                           override val metricsMonitoringService: MetricsMonitoringService = Mocks.mockMetricsMonitoringService,
                           override val ruleEngine: RuleEngine,
                           override val throttlingService: ThrottlingService = Mocks.mockThrottlingService,
                           override val auditConnector: AuditConnector = Mocks.mockAuditConnector,
                           auditContext: Option[TAuditContext] = None) extends RouterController {

  override protected def authConnector: AuthConnector = ???

  override def createAuditContext(): TAuditContext = auditContext.getOrElse(AuditContext())
}