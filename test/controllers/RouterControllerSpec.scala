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
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import services.{RuleService, WelcomePageService}
import uk.gov.hmrc.http.cache.client.ShortLivedCache
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RouterControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  "router controller" should {

    "return location provided by rules" in {

      //given
      val expectedLocation: Location = Location("/some/location", "name")
      val rules: List[Rule] = mock[List[Rule]]

      //and
      val name: String = "name"

      //and
      implicit val authContext: AuthContext = mock[AuthContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(("name", name))
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
      implicit lazy val ruleContext: RuleContext = new RuleContext(name)
      val auditContext = new AuditContext()

      //and
      val mockRuleService = mock[RuleService]
      when(mockRuleService.fireRules(eqTo(rules), any[AuthContext], any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(Some(expectedLocation))

      val controller = new TestRouteController(rules = rules, ruleService = mockRuleService)

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
      val name: String = "name"

      //and
      implicit val authContext: AuthContext = mock[AuthContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(("name", name))
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
      implicit lazy val ruleContext: RuleContext = new RuleContext(name)
      val auditContext = new AuditContext()

      //and
      val mockRuleService = mock[RuleService]
      when(mockRuleService.fireRules(eqTo(rules), eqTo(authContext), any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(None)

      val controller = new TestRouteController(rules = rules, defaultLocation = Location("/default/location", ""), ruleService = mockRuleService)

      //when
      val futureResult: Future[Result] = controller.route
      val result = await(futureResult)

      //then
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe "/default/location"

      verify(mockRuleService).fireRules(eqTo(rules), eqTo(authContext), eqTo(ruleContext), eqTo(auditContext))(eqTo(fakeRequest), any[HeaderCarrier])
    }
  }
}

class TestRouteController(override val rules: List[Rule],
                          override val defaultLocation: Location = Location("/", ""),
                          override val controllerMetrics: ControllerMetrics = ControllerMetricsStub,
                          override val ruleService: RuleService) extends RouterController with MockitoSugar {
  override val welcomePageService: WelcomePageService = WelcomePageServiceStub

  override protected def authConnector: AuthConnector = ???

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