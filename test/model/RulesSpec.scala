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

package model

import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor3
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import services.{RuleService, WelcomePageService}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RulesSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  "a rule" should {
    "return a None location whether it shouldn't be applied" in {

      val mockLocation = Some(mock[Location])
      val mockRuleService = mock[RuleService]

      val rule = new Rule {
        override def shouldApply(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier, ruleContext: RuleContext): Future[Boolean] = Future(false)

        override val defaultLocation: Option[Location] = mockLocation

        override val ruleService: RuleService = mockRuleService
      }

      implicit val authContext: AuthContext = mock[AuthContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
      implicit lazy val ruleContext: RuleContext = mock[RuleContext]

      val futureLocation: Future[Option[Location]] = rule.apply

      val location: Option[Location] = await(futureLocation)
      location shouldBe None

      verify(mockRuleService, never()).fireRules(any[List[Rule]])(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier], any[RuleContext])
    }

    "return the default location whether it should be applied and sub-rules are not applied" in {

      val mockDefaultLocation = Some(mock[Location])
      val mockRuleService = mock[RuleService]
      when(mockRuleService.fireRules(any[List[Rule]])(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier], any[RuleContext])).thenReturn(Future(None))

      val rule = new Rule {
        override def shouldApply(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier, ruleContext: RuleContext): Future[Boolean] = Future(true)

        override val defaultLocation: Option[Location] = mockDefaultLocation

        override val ruleService: RuleService = mockRuleService
      }

      implicit val authContext: AuthContext = mock[AuthContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
      implicit lazy val ruleContext: RuleContext = mock[RuleContext]

      val futureLocation: Future[Option[Location]] = rule.apply

      val location: Option[Location] = await(futureLocation)
      location shouldBe mockDefaultLocation

      verify(mockRuleService).fireRules(eqTo(List()))(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier], any[RuleContext])
    }

    "return the location provided by the sub-rules whether it should be applied and sub-rules are applied" in {

      val mockDefaultLocation = Some(mock[Location])
      val mockSubRulesLocation = Some(mock[Location])
      val mockRuleService = mock[RuleService]
      when(mockRuleService.fireRules(any[List[Rule]])(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier], any[RuleContext])).thenReturn(Future(mockSubRulesLocation))

      val rule = new Rule {
        override def shouldApply(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier, ruleContext: RuleContext): Future[Boolean] = Future(true)

        override val defaultLocation: Option[Location] = mockDefaultLocation

        override val ruleService: RuleService = mockRuleService
      }

      implicit val authContext: AuthContext = mock[AuthContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
      implicit lazy val ruleContext: RuleContext = mock[RuleContext]

      val futureLocation: Future[Option[Location]] = rule.apply

      val location: Option[Location] = await(futureLocation)
      location shouldBe mockSubRulesLocation

      verify(mockRuleService).fireRules(eqTo(List()))(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier], any[RuleContext])
    }
  }

  "GovernmentGatewayRule" should {

    "have a set of sub-rules" in {
      GovernmentGatewayRule.subRules shouldBe List(HasAnyBusinessEnrolment)
    }
    "have a default location" in {
      GovernmentGatewayRule.defaultLocation shouldBe Some(BTALocation)
    }
    "apply whether the token is present" in {
      val scenarios =
        Table(
          ("scenario", "tokenPresent", "expectedResult"),
          ("token is present", true, true),
          ("token is absent", false, false)
        )

      forAll(scenarios) { (scenario: String, tokenPresent: Boolean, expectedResult: Boolean) =>

        implicit val authContext: AuthContext = mock[AuthContext]
        implicit lazy val fakeRequest = tokenPresent match {
          case false => FakeRequest()
          case true => FakeRequest().withSession(("token", "token"))
        }
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
        implicit lazy val ruleContext: RuleContext = mock[RuleContext]

        val futureResult: Future[Boolean] = GovernmentGatewayRule.shouldApply
        val result: Boolean = await(futureResult)
        result shouldBe expectedResult
      }
    }
  }

  "HasAnyBusinessEnrolment" should {

    "have a set of sub-rules" in {
      HasAnyBusinessEnrolment.subRules shouldBe List()
    }
    "have a default location" in {
      HasAnyBusinessEnrolment.defaultLocation shouldBe Some(BTALocation)
    }
    "apply whether the active enrolments include any business enrolment" in {
      val scenarios: TableFor3[String, Set[String], Boolean] =
        Table(
          ("scenario", "enrolments", "expectedResult"),
          ("has business enrolments", Set("enr1"), true),
          ("has no business enrolments", Set(), false)
        )

      forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>

        implicit val authContext: AuthContext = mock[AuthContext]
        implicit lazy val fakeRequest = FakeRequest()
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
        implicit lazy val ruleContext: RuleContext = mock[RuleContext]
        when(ruleContext.activeEnrolments).thenReturn(Future(enrolments))

        val futureResult: Future[Boolean] = HasAnyBusinessEnrolment.shouldApply
        val result: Boolean = await(futureResult)
        result shouldBe expectedResult
      }
    }
  }

  "HasSelfAssessmentEnrolments" should {

    "have a set of sub-rules" in {
      HasSelfAssessmentEnrolments.subRules shouldBe List()
    }
    "have a default location" in {
      HasSelfAssessmentEnrolments.defaultLocation shouldBe None
    }
    "apply whether the active enrolments include any self assessment enrolment" in {
      val scenarios: TableFor3[String, Set[String], Boolean] =
        Table(
          ("scenario", "enrolments", "expectedResult"),
          ("has self assessment enrolments", Set("enr3"), true),
          ("has no self assessment  enrolments", Set(), false)
        )

      forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>

        implicit val authContext: AuthContext = mock[AuthContext]
        implicit lazy val fakeRequest = FakeRequest()
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
        implicit lazy val ruleContext: RuleContext = mock[RuleContext]
        when(ruleContext.activeEnrolments).thenReturn(Future(enrolments))

        val futureResult: Future[Boolean] = HasSelfAssessmentEnrolments.shouldApply
        val result: Boolean = await(futureResult)
        result shouldBe expectedResult
      }
    }
  }

  "WelcomePageRule" should {

    "have a set of sub-rules" in {
      WelcomePageRule.subRules shouldBe List()
    }
    "have a default location" in {
      WelcomePageRule.defaultLocation shouldBe Some(WelcomePageLocation)
    }
    "apply whether the active enrolments include any self assessment enrolment" in {
      val scenarios =
        Table(
          ("scenario", "shouldShowWelcomePage", "expectedResult"),
          ("has self assessment enrolments", true, true),
          ("has no self assessment  enrolments", false, false)
        )

      forAll(scenarios) { (scenario: String, shouldShowWelcomePage: Boolean, expectedResult: Boolean) =>

        implicit val authContext: AuthContext = mock[AuthContext]
        implicit lazy val fakeRequest = FakeRequest()
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
        implicit lazy val ruleContext: RuleContext = mock[RuleContext]

        val mockWelcomePageService = mock[WelcomePageService]
        when(mockWelcomePageService.shouldShowWelcomePage).thenReturn(Future(shouldShowWelcomePage))

        object WelcomePageRuleTest extends WelcomePageRule {
          override val welcomePageService: WelcomePageService = mockWelcomePageService
        }
        val futureResult: Future[Boolean] = WelcomePageRuleTest.shouldApply
        val result: Boolean = await(futureResult)
        result shouldBe expectedResult

        verify(mockWelcomePageService).shouldShowWelcomePage(eqTo(authContext), eqTo(hc))
      }
    }
  }

  "VerifyRule" should {

    "have a set of sub-rules" in {
      VerifyRule.subRules shouldBe List()
    }
    "have a default location" in {
      VerifyRule.defaultLocation shouldBe Some(PTALocation)
    }
    "apply whether the token is not present" in {
      val scenarios =
        Table(
          ("scenario", "tokenPresent", "expectedResult"),
          ("token is present", true, false),
          ("token is absent", false, true)
        )

      forAll(scenarios) { (scenario: String, tokenPresent: Boolean, expectedResult: Boolean) =>

        implicit val authContext: AuthContext = mock[AuthContext]
        implicit lazy val fakeRequest = tokenPresent match {
          case false => FakeRequest()
          case true => FakeRequest().withSession(("token", "token"))
        }
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
        implicit lazy val ruleContext: RuleContext = mock[RuleContext]

        val futureResult: Future[Boolean] = VerifyRule.shouldApply
        val result: Boolean = await(futureResult)
        result shouldBe expectedResult
      }
    }
  }
}
