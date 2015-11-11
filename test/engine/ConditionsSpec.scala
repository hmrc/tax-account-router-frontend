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

import connector.SaReturn
import model.RoutingReason._
import model._
import org.joda.time.DateTime
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor3
import org.scalatest.prop.Tables.Table
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeRequest}
import services.WelcomePageService
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConditionsSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  val configuration = Map[String, Any](
        "business-enrolments" -> List("enr1", "enr2"),
        "self-assessment-enrolments" -> List("enr3")
      )

  override lazy val fakeApplication: FakeApplication = FakeApplication(additionalConfiguration = configuration)

  "HasAnyBusinessEnrolment" should {

    "have an audit type specified" in {
      HasAnyBusinessEnrolment.auditType shouldBe Some(HAS_BUSINESS_ENROLMENTS)
    }

    val scenarios: TableFor3[String, Set[String], Boolean] =
      Table(
        ("scenario", "enrolments", "expectedResult"),
        ("has business enrolments", Set("enr1"), true),
        ("has no business enrolments", Set(), false)
      )

    forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>
      s"be true whether the user has any business enrolments - scenario: $scenario" in {

        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        val authContext = mock[AuthContext]

        val ruleContext = mock[RuleContext]
        when(ruleContext.activeEnrolments).thenReturn(Future(enrolments))

        val result = await(HasAnyBusinessEnrolment.isTrue(authContext, ruleContext))

        result shouldBe expectedResult
      }
    }
  }

  "HasSelfAssessmentEnrolments" should {

    "have an audit type specified" in {
      HasSelfAssessmentEnrolments.auditType shouldBe Some(HAS_SA_ENROLMENTS)
    }

    val scenarios: TableFor3[String, Set[String], Boolean] =
      Table(
        ("scenario", "enrolments", "expectedResult"),
        ("has self assessment enrolments", Set("enr3"), true),
        ("has no self assessment enrolments", Set(), false)
      )

    forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>

      implicit val fakeRequest = FakeRequest()
      implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val authContext = mock[AuthContext]

      s"be true whether the user has any self assessment enrolment - scenario: $scenario" in {
        running(FakeApplication(additionalConfiguration = configuration)) {

          lazy val ruleContext = mock[RuleContext]
          when(ruleContext.activeEnrolments).thenReturn(Future(enrolments))

          val futureResult: Future[Boolean] = HasSelfAssessmentEnrolments.isTrue(authContext, ruleContext)
          val result = await(futureResult)
          result shouldBe expectedResult
        }
      }
    }
  }

  "HasPreviousReturns" should {

    "have an audit type specified" in {
      HasPreviousReturns.auditType shouldBe Some(HAS_PREVIOUS_RETURNS)
    }

    val scenarios =
      Table(
        ("scenario", "lastSaReturn", "expectedResult"),
        ("has previous returns", SaReturn(previousReturns = true), true),
        ("has no previous returns", SaReturn.empty, false)
      )

    forAll(scenarios) { (scenario: String, lastSaReturn: SaReturn, expectedResult: Boolean) =>

      implicit val fakeRequest = FakeRequest()
      implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val authContext = mock[AuthContext]

      s"be true whether the user has any self assessment enrolment - scenario: $scenario" in {

        val ruleContext = mock[RuleContext]
        when(ruleContext.lastSaReturn).thenReturn(Future(lastSaReturn))

        val result = await(HasPreviousReturns.isTrue(authContext, ruleContext))
        result shouldBe expectedResult
      }
    }
  }

  "IsInAPartnership" should {

    "have an audit type specified" in {
      IsInAPartnership.auditType shouldBe Some(IS_IN_A_PARTNERSHIP)
    }

    val scenarios =
      Table(
        ("scenario", "lastSaReturn", "expectedResult"),
        ("is in a partnership", SaReturn(supplementarySchedules = List("partnership")), true),
        ("is not in a partnership", SaReturn.empty, false)
      )

    forAll(scenarios) { (scenario: String, lastSaReturn: SaReturn, expectedResult: Boolean) =>

      implicit val fakeRequest = FakeRequest()
      implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val authContext = mock[AuthContext]

      s"be true whether the user has a partnership supplementary schedule - scenario: $scenario" in {

        val ruleContext = mock[RuleContext]
        when(ruleContext.lastSaReturn).thenReturn(Future(lastSaReturn))

        val result = await(IsInAPartnership.isTrue(authContext, ruleContext))
        result shouldBe expectedResult
      }
    }
  }

  "IsSelfEmployed" should {

    "have an audit type specified" in {
      IsSelfEmployed.auditType shouldBe Some(IS_SELF_EMPLOYED)
    }

    val scenarios =
      Table(
        ("scenario", "lastSaReturn", "expectedResult"),
        ("is self employed", SaReturn(supplementarySchedules = List("self_employment")), true),
        ("is not self employed", SaReturn.empty, false)
      )

    forAll(scenarios) { (scenario: String, lastSaReturn: SaReturn, expectedResult: Boolean) =>

      implicit val fakeRequest = FakeRequest()
      implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val authContext = mock[AuthContext]

      s"be true whether the user has a self employment supplementary schedule - scenario: $scenario" in {

        val ruleContext = mock[RuleContext]
        when(ruleContext.lastSaReturn).thenReturn(Future(lastSaReturn))

        val result = await(IsSelfEmployed.isTrue(authContext, ruleContext))
        result shouldBe expectedResult
      }
    }
  }

  "LoggedInForTheFirstTime" should {

    "have an audit type specified" in {
      LoggedInForTheFirstTime.auditType shouldBe Some(LOGGED_IN_FOR_THE_FIRST_TIME)
    }

    val scenarios =
      Table(
        ("scenario", "previouslyLoggedInAt", "expectedResult"),
        ("Logged in for the first time", None, true),
        ("Not logged in for the first time", Some(DateTime.now), false)
      )

    forAll(scenarios) { (scenario: String, previouslyLoggedInAt: Option[DateTime], expectedResult: Boolean) =>

      implicit val fakeRequest = FakeRequest()
      implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val authContext = mock[AuthContext]

      s"be $expectedResult when $scenario" in {
        //given
        val ruleContext = mock[RuleContext]

        //and
        val user = mock[LoggedInUser]
        when(user.previouslyLoggedInAt).thenReturn(previouslyLoggedInAt)
        when(authContext.user).thenReturn(user)

        //when
        val result = await(LoggedInForTheFirstTime.isTrue(authContext, ruleContext))

        //then
        result shouldBe expectedResult
      }

    }

  }

  "HasNeverSeenWelcomeBefore" should {

    "have an audit type specified" in {
      HasNeverSeenWelcomeBefore.auditType shouldBe Some(HAS_NEVER_SEEN_WELCOME_PAGE_BEFORE)
    }

    val scenarios =
      Table(
        ("scenario", "hasNeverSeenTheWelcomePage", "expectedResult"),
        ("has logged in for the first time", true, true),
        ("it's not the first time user logs in", false, false)
      )

    forAll(scenarios) { (scenario: String, hasNeverSeenTheWelcomePage: Boolean, expectedResult: Boolean) =>

      implicit val fakeRequest = FakeRequest()
      implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val authContext = mock[AuthContext]

      s"be true whether the user has any self assessment enrolment - scenario: $scenario" in {

        val ruleContext = mock[RuleContext]

        val mockWelcomePageService = mock[WelcomePageService]
        when(mockWelcomePageService.hasNeverSeenWelcomePageBefore(eqTo(authContext))(eqTo(hc))).thenReturn(Future(hasNeverSeenTheWelcomePage))

        val condition = new HasNeverSeenWelcomeBefore {
          override val welcomePageService: WelcomePageService = mockWelcomePageService
        }

        val result = await(condition.isTrue(authContext, ruleContext))
        result shouldBe expectedResult
      }
    }
  }

  "LoggedInViaVerify" should {

    "have an audit type specified" in {
      LoggedInViaVerify.auditType shouldBe Some(IS_A_VERIFY_USER)
    }

    val scenarios =
      Table(
        ("scenario", "tokenPresent", "expectedResult"),
        ("has logged in using Verify", false, true),
        ("has not logged in using Verify", true, false)
      )

    forAll(scenarios) { (scenario: String, tokenPresent: Boolean, expectedResult: Boolean) =>

      val authContext = mock[AuthContext]

      s"be true whether the user has logged in using Verify - scenario: $scenario" in {

        running(FakeApplication(additionalConfiguration = configuration)) {
          implicit val fakeRequest = tokenPresent match {
            case false => FakeRequest()
            case true => FakeRequest().withSession(("token", "token"))
          }

          implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

          val ruleContext = mock[RuleContext]

          val result = await(LoggedInViaVerify.isTrue(authContext, ruleContext))
          result shouldBe expectedResult
        }
      }
    }
  }

  "LoggedInViaGovernmentGateway" should {

    "have an audit type specified" in {
      LoggedInViaGovernmentGateway.auditType shouldBe Some(IS_A_GOVERNMENT_GATEWAY_USER)
    }

    val scenarios =
      Table(
        ("scenario", "tokenPresent", "expectedResult"),
        ("has logged in using GG", true, true),
        ("has not logged in using GG", false, false)
      )

    forAll(scenarios) { (scenario: String, tokenPresent: Boolean, expectedResult: Boolean) =>

      val authContext = mock[AuthContext]

      s"be true whether the user has logged in using Verify - scenario: $scenario" in {

        running(FakeApplication(additionalConfiguration = configuration)) {
          implicit val fakeRequest = tokenPresent match {
            case false => FakeRequest()
            case true => FakeRequest().withSession(("token", "token"))
          }

          implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

          val ruleContext = mock[RuleContext]

          val result = await(LoggedInViaGovernmentGateway.isTrue(authContext, ruleContext))
          result shouldBe expectedResult
        }
      }
    }
  }

  "AnyOtherRuleApplied" should {

    "not have any audit type specified" in {
      AnyOtherRuleApplied.auditType shouldBe None
    }

    val authContext = mock[AuthContext]

    "always be true" in {

      implicit val fakeRequest = FakeRequest()
      implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val ruleContext = mock[RuleContext]

      val result = await(AnyOtherRuleApplied.isTrue(authContext, ruleContext))
      result shouldBe true
    }
  }
}
