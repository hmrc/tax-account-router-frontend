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

package engine

import controllers.TarRules
import helpers.SpecHelpers
import model.Location._
import model.RoutingReason.RoutingReason
import model._
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito.{when, _}
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RuleEngineSpec extends UnitSpec with MockitoSugar with WithFakeApplication with SpecHelpers with Eventually {

  case class BooleanCondition(b: Boolean) extends Condition {
    override val auditType: Option[RoutingReason] = None

    override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
      Future(b)
  }

  private val trueLocation: LocationType = evaluateUsingPlay(Location.Type("/true", "true"))
  val trueRule = When(BooleanCondition(true)).thenGoTo(trueLocation) withName "true-rule"
  private val falseLocation: LocationType = evaluateUsingPlay(Location.Type("/false", "false"))
  val falseRule = When(BooleanCondition(false)).thenGoTo(falseLocation) withName "false-rule"


  "a rule engine" should {

    "evaluate rules in order skipping those that should not be evaluated - should return /second/location" in {
      implicit lazy val request = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

      val auditContext: AuditContext = AuditContext()

      //when
      val maybeLocation: Future[Option[LocationType]] = new RuleEngine {
        override val rules: List[Rule] = List(falseRule, trueRule)
      }.getLocation(mock[AuthContext], mock[RuleContext], auditContext)(request, hc)

      //then
      val location: Option[LocationType] = await(maybeLocation)
      location shouldBe Some(trueLocation)

      eventually {
        auditContext.ruleApplied shouldBe trueRule.name
      }
    }

    "evaluate rules in order skipping those that should not be evaluated - should return /first/location" in {

      //given
      val firstRule = mock[Rule]
      val expectedLocation: LocationType = BusinessTaxAccount
      when(firstRule.apply(any[AuthContext], any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(Some(expectedLocation))
      when(firstRule.name) thenReturn "first-rule"
      val secondRule = mock[Rule]
      when(secondRule.apply(any[AuthContext], any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(None)

      //and
      implicit lazy val request = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

      val auditContext = AuditContext()

      //when
      val maybeLocation: Future[Option[LocationType]] = new RuleEngine {
        override val rules: List[Rule] = List(firstRule, secondRule)
      }.getLocation(mock[AuthContext], mock[RuleContext], auditContext)(request, hc)

      //then
      val location: Option[LocationType] = await(maybeLocation)
      location shouldBe Some(expectedLocation)

      //then
      verify(firstRule).apply(any[AuthContext], any[RuleContext], any[AuditContext])(eqTo(request), eqTo(hc))
      verify(secondRule, never()).apply(any[AuthContext], any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])

      eventually {
        auditContext.ruleApplied shouldBe "first-rule"
      }
    }
  }

  "the TAR rule engine" should {
    "have as last rule a tautology that redirects to BTA" in {

      val lastRule: Rule = TarRules.rules.last

      implicit val fakeRequest = FakeRequest()
      implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val mockAuthContext = mock[AuthContext]
      val mockRuleContext = mock[RuleContext]
      val mockAuditContext = mock[TAuditContext]

      val location: Option[LocationType] = await(lastRule.apply(mockAuthContext, mockRuleContext, mockAuditContext))

      location shouldBe Some(BusinessTaxAccount)
    }
  }
}


