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
import model._
import org.joda.time.{DateTime, DateTimeUtils, DateTimeZone}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import services.{RuleService, WelcomePageService}
import uk.gov.hmrc.http.cache.client.ShortLivedCache
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class RouterControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  val fixedDateTime = DateTime.now(DateTimeZone.UTC)

  override def beforeAll(): Unit = {
    super.beforeAll()
    DateTimeUtils.setCurrentMillisFixed(fixedDateTime.getMillis)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    DateTimeUtils.setCurrentMillisSystem()
  }

  "router controller" should {

    "return location provided by rules" in {

      //given
      val expectedLocation: Location = Location("/some/location", "location-name")
      val rules: List[Rule] = mock[List[Rule]]

      //and
      val userName: String = "userName"

      //and
      implicit val authContext: AuthContext = mock[AuthContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(("name", userName))
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
      implicit lazy val ruleContext: RuleContext = new RuleContext(userName)
      val auditContext = new AuditContext()

      //and
      val mockRuleService = mock[RuleService]
      when(mockRuleService.fireRules(eqTo(rules), any[AuthContext], any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(Some(expectedLocation))

      val controller = new TestRouterController(rules = rules, ruleService = mockRuleService)

      //when
      val futureResult: Future[Result] = controller.route
      val result = await(futureResult)

      //then
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe "/some/location"

      verify(mockRuleService).fireRules(eqTo(rules), eqTo(authContext), eqTo(ruleContext), eqTo(auditContext))(eqTo(fakeRequest), any[HeaderCarrier])
    }

    "return default location" in {

      //given
      val rules: List[Rule] = mock[List[Rule]]

      //and
      val userName: String = "userName"

      //and
      implicit val authContext: AuthContext = mock[AuthContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(("name", userName))
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
      implicit lazy val ruleContext: RuleContext = new RuleContext(userName)
      val auditContext = new AuditContext()

      //and
      val mockRuleService = mock[RuleService]
      when(mockRuleService.fireRules(eqTo(rules), eqTo(authContext), any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(None)

      val controller = new TestRouterController(rules = rules, defaultLocation = Location("/default/location", ""), ruleService = mockRuleService)

      //when
      val futureResult: Future[Result] = controller.route
      val result = await(futureResult)

      //then
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe "/default/location"

      verify(mockRuleService).fireRules(eqTo(rules), eqTo(authContext), eqTo(ruleContext), eqTo(auditContext))(eqTo(fakeRequest), any[HeaderCarrier])
    }

    "audit the event before redirecting" in {
      //given
      val rules: List[Rule] = mock[List[Rule]]
      val userName: String = "userName"

      //and
      implicit val authContext: AuthContext = mock[AuthContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(("name", userName))
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
      implicit lazy val ruleContext: RuleContext = new RuleContext(userName)
      val auditContext = new AuditContext()

      //and
      val mockRuleService = mock[RuleService]
      when(mockRuleService.fireRules(eqTo(rules), eqTo(authContext), any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(None)

      val mockAuditConnector = mock[AuditConnector]
      val controller = new TestRouterController(rules = rules, defaultLocation = Location("/default/location", ""), ruleService = mockRuleService, _auditConnector = Some(mockAuditConnector))

      //when
      await(controller.route)

      //then
      val auditEventCaptor: ArgumentCaptor[ExtendedDataEvent] = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      verify(mockAuditConnector).sendEvent(auditEventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext])

      val capturedAuditEvent = auditEventCaptor.getValue
      capturedAuditEvent.auditSource shouldBe "tax-account-router-frontend"
      capturedAuditEvent.auditType shouldBe "Routing"
      capturedAuditEvent.eventId should fullyMatch regex """^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$""".r
      capturedAuditEvent.tags.contains("clientIP")
      capturedAuditEvent.tags.contains("path")
      capturedAuditEvent.tags.contains("X-Session-ID")
      capturedAuditEvent.tags.contains("X-Request-ID")
      capturedAuditEvent.tags.contains("clientPort")
      capturedAuditEvent.tags.get("transactionName") shouldBe Some("transaction-name")
      capturedAuditEvent.detail shouldBe Json.obj("destination" -> "/default/location", "reasons" -> AuditContext.defaultReasons.toMap[String, String])
      capturedAuditEvent.generatedAt shouldBe fixedDateTime
    }
  }
}

class TestRouterController(override val rules: List[Rule],
                          override val defaultLocation: Location = Location("/", ""),
                          override val controllerMetrics: ControllerMetrics = ControllerMetricsStub,
                           override val ruleService: RuleService,
                           _auditConnector: Option[AuditConnector] = None) extends RouterController with MockitoSugar {
  override val welcomePageService: WelcomePageService = WelcomePageServiceStub

  override protected def authConnector: AuthConnector = ???

  override val auditConnector: AuditConnector = _auditConnector.getOrElse(mock[AuditConnector])

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