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

import connector.SaReturn
import model.AuditEventType._
import model.Location._
import org.mockito.ArgumentCaptor
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
import scala.concurrent.{ExecutionContext, Future}

class RulesSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  "a rule" should {
    "return a None location whether it shouldn't be applied" in {

      val mockLocation = Some(mock[LocationType])
      val mockRuleService = mock[RuleService]
      val auditContext = mock[AuditContext]

      val rule = RuleTest(mockLocation, mockRuleService, shouldApplyValue = false)

      val authContext: AuthContext = mock[AuthContext]
      lazy val ruleContext: RuleContext = mock[RuleContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val futureLocation: Future[Option[LocationType]] = rule.apply(authContext, ruleContext, auditContext)

      val location: Option[LocationType] = await(futureLocation)
      location shouldBe None

      verify(mockRuleService, never()).fireRules(any[List[Rule]], any[AuthContext], any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])
    }

    "return the default location whether it should be applied and sub-rules are not applied" in {

      val mockDefaultLocation = Some(mock[LocationType])
      val mockRuleService = mock[RuleService]
      val auditContext = mock[AuditContext]

      when(mockRuleService.fireRules(any[List[Rule]], any[AuthContext], any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Future(None))

      val rule = RuleTest(mockDefaultLocation, mockRuleService, shouldApplyValue = true)

      val authContext: AuthContext = mock[AuthContext]
      lazy val ruleContext: RuleContext = mock[RuleContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val futureLocation: Future[Option[LocationType]] = rule.apply(authContext, ruleContext, auditContext)

      val location: Option[LocationType] = await(futureLocation)
      location shouldBe mockDefaultLocation

      verify(mockRuleService).fireRules(eqTo(List()), any[AuthContext], any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])
    }

    "return the location provided by the sub-rules whether it should be applied and sub-rules are applied" in {

      val mockDefaultLocation = Some(mock[LocationType])
      val mockSubRulesLocation = Some(mock[LocationType])
      val auditContext = AuditContext()

      val mockRuleService = mock[RuleService]
      when(mockRuleService.fireRules(any[List[Rule]], any[AuthContext], any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Future(mockSubRulesLocation))

      val rule = RuleTest(mockDefaultLocation, mockRuleService, shouldApplyValue = true)

      val authContext: AuthContext = mock[AuthContext]
      lazy val ruleContext: RuleContext = mock[RuleContext]
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val futureLocation: Future[Option[LocationType]] = rule.apply(authContext, ruleContext, auditContext)

      val location: Option[LocationType] = await(futureLocation)
      location shouldBe mockSubRulesLocation

      verify(mockRuleService).fireRules(eqTo(List()), any[AuthContext], any[RuleContext], eqTo(auditContext))(any[Request[AnyContent]], any[HeaderCarrier])
    }
  }

  case class RuleTest(mockDefaultLocation: Option[LocationType], mockRuleService: RuleService, shouldApplyValue: Boolean) extends Rule {
    override def shouldApply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(shouldApplyValue)

    override val defaultLocation: Option[LocationType] = mockDefaultLocation

    override val ruleService: RuleService = mockRuleService
  }

  "GovernmentGatewayRule" should {

    "have a set of sub-rules" in {
      GovernmentGatewayRule.subRules shouldBe List(HasAnyBusinessEnrolment, HasSelfAssessmentEnrolments)
    }
    "have a default location" in {
      GovernmentGatewayRule.defaultLocation shouldBe Some(BTA)
    }

    val scenarios =
      Table(
        ("scenario", "tokenPresent", "expectedResult"),
        ("token is present", true, true),
        ("token is absent", false, false)
      )

    forAll(scenarios) { (scenario: String, tokenPresent: Boolean, expectedResult: Boolean) =>

      s"apply whether the token is present - scenario: $scenario" in {

        val mockAuditContext = mock[TAuditContext]
        val authContext: AuthContext = mock[AuthContext]
        lazy val ruleContext: RuleContext = mock[RuleContext]
        implicit lazy val fakeRequest = tokenPresent match {
          case false => FakeRequest()
          case true => FakeRequest().withSession(("token", "token"))
        }
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        val futureResult: Future[Boolean] = GovernmentGatewayRule.shouldApply(authContext, ruleContext, mockAuditContext)
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
      HasAnyBusinessEnrolment.defaultLocation shouldBe Some(BTA)
    }

    val scenarios: TableFor3[String, Set[String], Boolean] =
      Table(
        ("scenario", "enrolments", "expectedResult"),
        ("has business enrolments", Set("enr1"), true),
        ("has no business enrolments", Set(), false)
      )

    forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>

      s"apply whether the active enrolments include any business enrolment - scenario: $scenario" in {
        //given
        val mockAuditContext = mock[TAuditContext]
        val authContext: AuthContext = mock[AuthContext]
        lazy val ruleContext: RuleContext = mock[RuleContext]
        implicit lazy val fakeRequest = FakeRequest()
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
        when(ruleContext.activeEnrolments).thenReturn(Future(enrolments))

        //when
        val futureResult: Future[Boolean] = HasAnyBusinessEnrolment.shouldApply(authContext, ruleContext, mockAuditContext)

        //then
        val result: Boolean = await(futureResult)
        result shouldBe expectedResult

        //and
        mockAuditContext.verifySetValue(auditEventType = HAS_BUSINESS_ENROLMENTS, valueType = Boolean, expectedValue = expectedResult)
        verifyNoMoreInteractions(mockAuditContext)
      }
    }
  }

  "HasSelfAssessmentEnrolments" should {

    "have a set of sub-rules" in {
      HasSelfAssessmentEnrolments.subRules shouldBe List(WithNoPreviousReturns, IsInPartnershipOrSelfEmployed, IsNotInPartnershipNorSelfEmployed)
    }
    "have a default location" in {
      HasSelfAssessmentEnrolments.defaultLocation shouldBe None
    }

    val scenarios: TableFor3[String, Set[String], Boolean] =
      Table(
        ("scenario", "enrolments", "expectedResult"),
        ("has self assessment enrolments", Set("enr3"), true),
        ("has no self assessment  enrolments", Set(), false)
      )

    forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>

      s"apply whether the active enrolments include any self assessment enrolment - scenario: $scenario" in {

        val auditContext = AuditContext()
        val authContext: AuthContext = mock[AuthContext]
        lazy val ruleContext: RuleContext = mock[RuleContext]
        implicit lazy val fakeRequest = FakeRequest()
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)
        when(ruleContext.activeEnrolments).thenReturn(Future(enrolments))

        val futureResult: Future[Boolean] = HasSelfAssessmentEnrolments.shouldApply(authContext, ruleContext, auditContext)
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
      WelcomePageRule.defaultLocation shouldBe Some(WELCOME)
    }

    val scenarios =
      Table(
        ("scenario", "shouldShowWelcomePage", "expectedResult"),
        ("welcome page not visited before", true, true),
        ("welcome page visited before", false, false)
      )

    forAll(scenarios) { (scenario: String, shouldShowWelcomePage: Boolean, expectedResult: Boolean) =>

      s"apply whether route to the welcome page - scenario: $scenario" in {

        //given
        val mockAuditContext = mock[TAuditContext]
        val authContext: AuthContext = mock[AuthContext]
        lazy val ruleContext: RuleContext = mock[RuleContext]
        implicit lazy val fakeRequest = FakeRequest()
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        //and
        val mockWelcomePageService = mock[WelcomePageService]
        when(mockWelcomePageService.shouldShowWelcomePage(authContext, hc)).thenReturn(Future(shouldShowWelcomePage))

        object WelcomePageRuleTest extends WelcomePageRule {
          override val welcomePageService: WelcomePageService = mockWelcomePageService
        }

        //when
        val futureResult: Future[Boolean] = WelcomePageRuleTest.shouldApply(authContext, ruleContext, mockAuditContext)

        //then
        val result: Boolean = await(futureResult)
        result shouldBe expectedResult

        verify(mockWelcomePageService).shouldShowWelcomePage(eqTo(authContext), eqTo(hc))

        //and
        mockAuditContext.verifySetValue(auditEventType = HAS_ALREADY_SEEN_WELCOME_PAGE, valueType = Boolean, expectedValue = !shouldShowWelcomePage)
        verifyNoMoreInteractions(mockAuditContext)
      }
    }
  }

  "VerifyRule" should {

    "have a set of sub-rules" in {
      VerifyRule.subRules shouldBe List()
    }
    "have a default location" in {
      VerifyRule.defaultLocation shouldBe Some(PTA)
    }

    val scenarios =
      Table(
        ("scenario", "tokenPresent", "expectedResult"),
        ("token is present", true, false),
        ("token is absent", false, true)
      )

    forAll(scenarios) { (scenario: String, tokenPresent: Boolean, expectedResult: Boolean) =>

      s"apply whether the token is not present - scenario: $scenario" in {

        val auditContext = AuditContext()
        val authContext: AuthContext = mock[AuthContext]
        lazy val ruleContext: RuleContext = mock[RuleContext]
        implicit lazy val fakeRequest = tokenPresent match {
          case false => FakeRequest()
          case true => FakeRequest().withSession(("token", "token"))
        }
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        val futureResult: Future[Boolean] = VerifyRule.shouldApply(authContext, ruleContext, auditContext)
        val result: Boolean = await(futureResult)
        result shouldBe expectedResult
      }
    }
  }



  "IsInPartnershipOrSelfEmployed" should {

    "have a set of sub-rules" in {
      IsInPartnershipOrSelfEmployed.subRules shouldBe List()
    }
    "have a default location" in {
      IsInPartnershipOrSelfEmployed.defaultLocation shouldBe Some(BTA)
    }

    val scenarios =
      Table(
        ("scenario", "schedules", "previousReturns", "expectedResult"),
        ("with previous returns and in partnership not self employed", List("partnership"), true, true),
        ("with previous returns and not in partnership and self employed", List("self_employment"), true, true),
        ("with previous returns and in partnership and self employed", List("partnership", "self_employment"), true, true),
        ("with previous returns and not in partnership nor self employed", List.empty, true, false),
        ("with previous no returns and in partnership not self employed", List("partnership"), false, false),
        ("with previous no returns and not in partnership and self employed", List("self_employment"), false, false),
        ("with previous no returns and in partnership and self employed", List("partnership", "self_employment"), false, false),
        ("with previous no returns and not in partnership nor self employed", List.empty, false, false)
      )

    forAll(scenarios) { (scenario: String, schedules: List[String], previousReturns: Boolean, expectedResult: Boolean) =>
      s"apply whether the user is in a partnership or is self-employed - scenario: $scenario" in {
        //given
        val mockAuditContext = mock[TAuditContext]
        val authContext: AuthContext = mock[AuthContext]
        implicit lazy val fakeRequest = FakeRequest()
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        //and
        lazy val ruleContext: RuleContext = mock[RuleContext]


        val saReturn: SaReturn = SaReturn(schedules, previousReturns = previousReturns)

        when(ruleContext.lastSaReturn).thenReturn(saReturn)

        //when
        val futureResult: Future[Boolean] = IsInPartnershipOrSelfEmployed.shouldApply(authContext, ruleContext, mockAuditContext)

        //then
        val result: Boolean = await(futureResult)
        result shouldBe expectedResult

        //and
        verify(ruleContext).lastSaReturn

        //and
        mockAuditContext.verifySetValue(auditEventType = IS_IN_A_PARTNERSHIP, valueType = Boolean, expectedValue = saReturn.partnership)
        mockAuditContext.verifySetValue(auditEventType = IS_SELF_EMPLOYED, valueType = Boolean, expectedValue = saReturn.selfEmployment)
        mockAuditContext.verifySetValue(auditEventType = HAS_PREVIOUS_RETURNS, valueType = Boolean, expectedValue = previousReturns)
        verifyNoMoreInteractions(mockAuditContext)
      }
    }
  }

  "IsNotInPartnershipNorSelfEmployed" should {

    "have a set of sub-rules" in {
      IsNotInPartnershipNorSelfEmployed.subRules shouldBe List()
    }
    "have a default location" in {
      IsNotInPartnershipNorSelfEmployed.defaultLocation shouldBe Some(PTA)
    }

    val scenarios =
      Table(
        ("scenario", "schedules", "previousReturns", "expectedResult"),
        ("with previous returns and in partnership not self employed", List("partnership"), true, false),
        ("with previous returns and not in partnership and self employed", List("self_employment"), true, false),
        ("with previous returns and in partnership and self employed", List("partnership", "self_employment"), true, false),
        ("with previous returns and not in partnership nor self employed", List.empty, true, true),
        ("with previous no returns and in partnership not self employed", List("partnership"), false, false),
        ("with previous no returns and not in partnership and self employed", List("self_employment"), false, false),
        ("with previous no returns and in partnership and self employed", List("partnership", "self_employment"), false, false),
        ("with previous no returns and not in partnership nor self employed", List.empty, false, false)
      )

    forAll(scenarios) { (scenario: String, schedules: List[String], previousReturns: Boolean, expectedResult: Boolean) =>

      s"apply whether the user is not in a partnership nor self-employed - scenario: $scenario" in {
        //given
        val mockAuditContext = mock[TAuditContext]
        val authContext: AuthContext = mock[AuthContext]
        implicit lazy val fakeRequest = FakeRequest()
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        //and
        lazy val ruleContext: RuleContext = mock[RuleContext]
        val saReturn: SaReturn = SaReturn(schedules, previousReturns = previousReturns)
        when(ruleContext.lastSaReturn).thenReturn(saReturn)

        //when
        val futureResult: Future[Boolean] = IsNotInPartnershipNorSelfEmployed.shouldApply(authContext, ruleContext, mockAuditContext)

        //then
        val result: Boolean = await(futureResult)
        result shouldBe expectedResult

        //and
        verify(ruleContext).lastSaReturn

        //and
        mockAuditContext.verifySetValue(auditEventType = IS_IN_A_PARTNERSHIP, valueType = Boolean, expectedValue = saReturn.partnership)
        mockAuditContext.verifySetValue(auditEventType = IS_SELF_EMPLOYED, valueType = Boolean, expectedValue = saReturn.selfEmployment)
        mockAuditContext.verifySetValue(auditEventType = HAS_PREVIOUS_RETURNS, valueType = Boolean, expectedValue = previousReturns)
        verifyNoMoreInteractions(mockAuditContext)
      }
    }
  }

  "WithNoPreviousReturns" should {

    "have a set of sub-rules" in {
      WithNoPreviousReturns.subRules shouldBe List()
    }
    "have a default location" in {
      WithNoPreviousReturns.defaultLocation shouldBe Some(BTA)
    }

    val scenarios =
      Table(
        ("scenario", "previousReturns", "expectedResult"),
        ("has previous returns", true, false),
        ("has no previous returns", false, true)
      )

    forAll(scenarios) { (scenario: String, previousReturns: Boolean, expectedResult: Boolean) =>

      s"apply whether user does not have previous returns - scenario: $scenario" in {
        //given
        val mockAuditContext = mock[TAuditContext]
        val authContext: AuthContext = mock[AuthContext]
        implicit lazy val fakeRequest = FakeRequest()
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        //and
        lazy val ruleContext: RuleContext = mock[RuleContext]
        when(ruleContext.lastSaReturn).thenReturn(SaReturn(previousReturns = previousReturns))

        //when
        val futureResult: Future[Boolean] = WithNoPreviousReturns.shouldApply(authContext, ruleContext, mockAuditContext)

        //then
        val result: Boolean = await(futureResult)
        result shouldBe expectedResult

        //and
        verify(ruleContext).lastSaReturn

        //and
        mockAuditContext.verifySetValue(auditEventType = HAS_PREVIOUS_RETURNS, valueType = Boolean, expectedValue = previousReturns)
        verifyNoMoreInteractions(mockAuditContext)
      }
    }
  }

  implicit class MockAuditContextVerifier(mockAuditContext: TAuditContext) {

    def verifySetValue[T](auditEventType: AuditEventType, valueType: T, expectedValue: Boolean): Unit = {
      val captor = ArgumentCaptor.forClass(classOf[Future[T]])
      verify(mockAuditContext).setValue(eqTo(auditEventType), captor.capture())(any[ExecutionContext])
      await(captor.getValue) shouldBe expectedValue
    }
  }

}
