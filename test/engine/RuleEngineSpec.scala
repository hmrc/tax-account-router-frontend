/*
 * Copyright 2016 HM Revenue & Customs
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
import model.Locations._
import model.RoutingReason.RoutingReason
import model.{Location, _}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito.{when, _}
import org.scalatest.mock.MockitoSugar
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RuleEngineSpec extends UnitSpec with MockitoSugar with WithFakeApplication with SpecHelpers {

  case class BooleanCondition(b: Boolean) extends Condition {
    override val auditType: Option[RoutingReason] = None

    override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
      Future(b)
  }

  val trueLocation: Location = evaluateUsingPlay(Location("/true", "true"))
  val trueRule = When(BooleanCondition(true)).thenGoTo(trueLocation) withName "true-rule"
  val falseLocation: Location = evaluateUsingPlay(Location("/false", "false"))
  val falseRule = When(BooleanCondition(false)).thenGoTo(falseLocation) withName "false-rule"

  val theDefaultLocation = Location("default-location", "/default-location")


  "a rule engine" should {

    "evaluate rules in order skipping those that should not be evaluated - should return /second/location" in {
      implicit lazy val request = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

      val auditContext: AuditContext = AuditContext()

      //when
      val locationFuture: Future[Location] = new RuleEngine {
        override val defaultLocation = theDefaultLocation
        override val rules: List[Rule] = List(falseRule, trueRule)
      }.matchRulesForLocation(mock[RuleContext], auditContext)(request, hc)

      //then
      await(locationFuture) shouldBe trueLocation

      auditContext.ruleApplied shouldBe trueRule.name
    }

    "evaluate empty rules and return with default location" in {
      implicit lazy val request = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

      val auditContext: AuditContext = AuditContext()

      //when
      val locationFuture: Future[Location] = new RuleEngine {
        override val defaultLocation = theDefaultLocation
        override val rules: List[Rule] = List()
      }.matchRulesForLocation(mock[RuleContext], auditContext)(request, hc)

      //then
      await(locationFuture) shouldBe theDefaultLocation

      auditContext.ruleApplied shouldBe ""
    }

    "evaluate rules in order skipping those that should not be evaluated - should return /first/location" in {

      //given
      val firstRule = mock[Rule]
      val expectedLocation: Location = BusinessTaxAccount
      when(firstRule.apply(any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(Some(expectedLocation))
      when(firstRule.name) thenReturn "first-rule"
      val secondRule = mock[Rule]
      when(secondRule.apply(any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(None)

      //and
      implicit lazy val request = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

      val auditContext = AuditContext()

      //when
      val locationFuture: Future[Location] = new RuleEngine {
        override val defaultLocation = theDefaultLocation
        override val rules: List[Rule] = List(firstRule, secondRule)
      }.matchRulesForLocation(mock[RuleContext], auditContext)(request, hc)

      //then
      await(locationFuture) shouldBe expectedLocation

      //then
      verify(firstRule).apply(any[RuleContext], any[AuditContext])(eqTo(request), eqTo(hc))
      verify(secondRule, never()).apply(any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])

      auditContext.ruleApplied shouldBe "first-rule"
    }
  }

  "the TAR rule engine" should {
    "have as last rule a tautology that redirects to BTA" in {

      val lastRule: Rule = TarRules.rules.last

      implicit val fakeRequest = FakeRequest()
      implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val mockRuleContext = mock[RuleContext]
      val mockAuditContext = mock[TAuditContext]

      val location: Option[Location] = await(lastRule.apply(mockRuleContext, mockAuditContext))

      location shouldBe Some(BusinessTaxAccount)
    }
  }
}
