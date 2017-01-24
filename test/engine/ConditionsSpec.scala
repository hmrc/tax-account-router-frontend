/*
 * Copyright 2017 HM Revenue & Customs
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

import connector._
import model.RoutingReason._
import model._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConditionsSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  val configuration = Map[String, Any](
    "business-enrolments" -> "enr1,enr2",
    "self-assessment-enrolments" -> "enr3"
  )

  override lazy val fakeApplication: FakeApplication = FakeApplication(additionalConfiguration = configuration)

  "HasAnyBusinessEnrolment" should {

    "have an audit type specified" in {
      HasAnyBusinessEnrolment.auditType shouldBe Some(HAS_BUSINESS_ENROLMENTS)
    }

    val scenarios =
      Table(
        ("scenario", "enrolments", "expectedResult"),
        ("has business enrolments", Set("enr1"), true),
        ("has no business enrolments", Set.empty[String], false)
      )

    forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>
      s"be true whether the user has any business enrolments - scenario: $scenario" in {

        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        val ruleContext = mock[RuleContext]
        when(ruleContext.activeEnrolmentKeys).thenReturn(Future(enrolments))
        val hasAnyBusinessEnrolment = new HasAnyBusinessEnrolment {
          override val businessEnrolments = Set("enr1", "enr2")
        }

        val result = await(hasAnyBusinessEnrolment.isTrue(ruleContext))

        result shouldBe expectedResult
      }
    }
  }

  "HasSaEnrolments" should {

    "have an audit type specified" in {
      HasSaEnrolments.auditType.get.key shouldBe "has-self-assessment-enrolments"
    }

    val scenarios =
      Table(
        ("scenario", "enrolments", "expectedResult"),
        ("user does not have SA enrolments", Set("enr1"), false),
        ("user has SA enrolments", Set("enr3", "enr4"), true)
      )

    forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>
      s"return $expectedResult when $scenario" in {
        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        lazy val ruleContext = mock[RuleContext]
        when(ruleContext.activeEnrolmentKeys).thenReturn(Future(enrolments))

        val result = await(HasSaEnrolments.isTrue(ruleContext))

        result shouldBe expectedResult
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

      s"be true whether the user has any self assessment enrolment - scenario: $scenario" in {

        val ruleContext = mock[RuleContext]
        when(ruleContext.lastSaReturn).thenReturn(Future(lastSaReturn))

        val result = await(HasPreviousReturns.isTrue(ruleContext))
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

      s"be true whether the user has a partnership supplementary schedule - scenario: $scenario" in {

        val ruleContext = mock[RuleContext]
        when(ruleContext.lastSaReturn).thenReturn(Future(lastSaReturn))

        val result = await(IsInAPartnership.isTrue(ruleContext))
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

      s"be true whether the user has a self employment supplementary schedule - scenario: $scenario" in {

        val ruleContext = mock[RuleContext]
        when(ruleContext.lastSaReturn).thenReturn(Future(lastSaReturn))

        val result = await(IsSelfEmployed.isTrue(ruleContext))
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
        ("scenario", "tokenPresent", "comingWithCredId", "expectedResult"),
        ("has no token and not coming with credId", false, false, true),
        ("has no token and coming with credId", false, true, false),
        ("has token and not coming with credId", true, false, false)
      )

    forAll(scenarios) { (scenario: String, tokenPresent: Boolean, comingWithCredId: Boolean,  expectedResult: Boolean) =>

      s"be true whether the user has logged in using Verify - scenario: $scenario" in {

        implicit val fakeRequest = tokenPresent match {
          case false => FakeRequest()
          case true => FakeRequest().withSession(("token", "token"))
        }

        implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        val ruleContext = mock[RuleContext]
        when(ruleContext.credId).thenReturn(if(comingWithCredId) Some("credId") else None)

        val result = await(LoggedInViaVerify.isTrue(ruleContext))
        result shouldBe expectedResult
      }
    }
  }

  "IsAGovernmentGatewayUser" should {

    "have an audit type specified" in {
      IsAGovernmentGatewayUser.auditType shouldBe Some(IS_A_GOVERNMENT_GATEWAY_USER)
    }

    val scenarios =
      Table(
        ("scenario", "tokenPresent", "comingWithCredId", "expectedResult"),
        ("has logged in using GG", true, false, true),
        ("has not logged in using GG but coming with credId", false, true, true),
        ("has not logged in using GG and not coming with credId", false, false, false)
      )

    forAll(scenarios) { (scenario: String, tokenPresent: Boolean, comingWithCredId: Boolean, expectedResult: Boolean) =>

      s"be true whether the user has logged in using Verify - scenario: $scenario" in {

        implicit val fakeRequest = tokenPresent match {
          case false => FakeRequest()
          case true => FakeRequest().withSession(("token", "token"))
        }

        implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        val ruleContext = mock[RuleContext]
        when(ruleContext.credId).thenReturn(if(comingWithCredId) Some("credId") else None)

        val result = await(IsAGovernmentGatewayUser.isTrue(ruleContext))
        result shouldBe expectedResult
      }
    }
  }

  "HasNino" should {

    "have an audit type specified" in {
      HasNino.auditType shouldBe Some(HAS_NINO)
    }

    val scenarios =
      Table(
        ("scenario", "ninoPresent", "expectedResult"),
        ("user has a NINO", true, true),
        ("user has no NINO", false, false)
      )

    forAll(scenarios) { (scenario: String, ninoPresent: Boolean, expectedResult: Boolean) =>

      s"be true whether the user has a NINO - scenario: $scenario" in {
        val ruleContext = mock[RuleContext]
        val authMock = mock[UserAuthority]

        val nino = if (ninoPresent) Some(Nino("AA123456C")) else None
        when(authMock.nino).thenReturn(Future.successful(nino))
        when(ruleContext.authority).thenReturn(Future.successful(authMock))

        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        val result = await(HasNino.isTrue(ruleContext))
        result shouldBe expectedResult

        verify(ruleContext).authority
        verifyNoMoreInteractions(ruleContext)
      }
    }
  }

  "GGEnrolmentsAvailable" should {

    "have an audit type specified" in {
      GGEnrolmentsAvailable.auditType shouldBe Some(GG_ENROLMENTS_AVAILABLE)
    }

    val scenarios =
      Table(
        ("scenario", "ggEnrolmentsAvailable"),
        ("be true when GG is available", true),
        ("be false GG is not available", false)
      )

    forAll(scenarios) { (scenario: String, ggEnrolmentsAvailable: Boolean) =>

      scenario in {
        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        val ruleContext = mock[RuleContext]

        val expectedResult = ggEnrolmentsAvailable match {
          case true => Future.successful(Seq.empty[GovernmentGatewayEnrolment])
          case false => Future.failed(new RuntimeException())
        }
        when(ruleContext.enrolments).thenReturn(expectedResult)

        val result = await(GGEnrolmentsAvailable.isTrue(ruleContext))

        result shouldBe ggEnrolmentsAvailable
        verify(ruleContext).enrolments
        verifyNoMoreInteractions(ruleContext)
      }
    }
  }

  "AffinityGroupNotAvailable" should {

    "have an audit type specified" in {
      AffinityGroupAvailable.auditType shouldBe Some(AFFINITY_GROUP_AVAILABLE)
    }

    val scenarios =
      Table(
        ("scenario", "affinityGroupAvailable"),
        ("be true when affinity group is available", true),
        ("be false when affinity group is not available", false)
      )

    forAll(scenarios) { (scenario: String, affinityGroupAvailable: Boolean) =>

      scenario in {
        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        val ruleContext = mock[RuleContext]

        val expectedResult = affinityGroupAvailable match {
          case true => Future.successful("some-affinity-group")
          case false => Future.failed(new RuntimeException())
        }
        when(ruleContext.affinityGroup).thenReturn(expectedResult)

        val result = await(AffinityGroupAvailable.isTrue(ruleContext))

        result shouldBe affinityGroupAvailable
        verify(ruleContext).affinityGroup
        verifyNoMoreInteractions(ruleContext)
      }
    }
  }

  "SAReturnAvailable" should {

    "have an audit type specified" in {
      SAReturnAvailable.auditType shouldBe Some(SA_RETURN_AVAILABLE)
    }

    val scenarios =
      Table(
        ("scenario", "saReturnAvailable"),
        ("be true when SA is available", true),
        ("be false SA is not available", false)
      )

    forAll(scenarios) { (scenario: String, saReturnAvailable: Boolean) =>

      scenario in {
        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        val ruleContext = mock[RuleContext]

        val expectedResult = saReturnAvailable match {
          case true => Future.successful(mock[SaReturn])
          case false => Future.failed(new RuntimeException())
        }
        when(ruleContext.lastSaReturn).thenReturn(expectedResult)

        val result = await(SAReturnAvailable.isTrue(ruleContext))

        result shouldBe saReturnAvailable
        verify(ruleContext).lastSaReturn
        verifyNoMoreInteractions(ruleContext)
      }
    }
  }

  "AnyOtherRuleApplied" should {

    "not have any audit type specified" in {
      AnyOtherRuleApplied.auditType shouldBe None
    }

    "always be true" in {

      implicit val fakeRequest = FakeRequest()
      implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val ruleContext = mock[RuleContext]

      val result = await(AnyOtherRuleApplied.isTrue(ruleContext))
      result shouldBe true
    }
  }

  "HasIndividualAffinityGroup" should {

    "have an audit type specified" in {
      HasIndividualAffinityGroup.auditType shouldBe Some(HAS_INDIVIDUAL_AFFINITY_GROUP)
    }

    val scenarios =
      Table(
        ("scenario", "affinityGroup", "expectedResult"),
        ("return true when affinity group is 'Individual'", AffinityGroupValue.INDIVIDUAL, true),
        ("return false when affinity group is 'Organisation'", AffinityGroupValue.ORGANISATION, false)
      )

    forAll(scenarios) { (scenario: String, affinityGroup: String, expectedResult: Boolean) =>
      scenario in {
        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        lazy val ruleContext = mock[RuleContext]
        when(ruleContext.affinityGroup).thenReturn(Future(affinityGroup))

        val result = await(HasIndividualAffinityGroup.isTrue(ruleContext))

        result shouldBe expectedResult
      }
    }
  }

  "HasAnyInactiveEnrolment" should {

    "have an audit type specified" in {
      HasAnyInactiveEnrolment.auditType shouldBe Some(HAS_ANY_INACTIVE_ENROLMENT)
    }

    val scenarios =
      Table(
        ("scenario", "enrolments", "expectedResult"),
        ("return false when no inactive enrolments", Set.empty[String], false),
        ("return true when there's at least one inactive enrolment", Set("enr1"), true)
      )

    forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>
      scenario in {
        implicit val fakeRequest = FakeRequest()
        implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        lazy val ruleContext = mock[RuleContext]
        when(ruleContext.notActivatedEnrolmentKeys).thenReturn(Future.successful(enrolments))

        val result = await(HasAnyInactiveEnrolment.isTrue(ruleContext))

        result shouldBe expectedResult
      }
    }
  }

}
