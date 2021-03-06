/*
 * Copyright 2021 HM Revenue & Customs
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

import config.FrontendAppConfig
import connector._
import model._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import support.UnitSpec
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class ConditionsSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite with ScalaFutures {

  val configuration: Map[String, Any] = Map[String, Any](
    "business-enrolments" -> "enr1,enr2",
    "self-assessment-enrolments" -> "enr3"
  )

  val conf: FrontendAppConfig = new GuiceApplicationBuilder().configure(configuration).injector.instanceOf[FrontendAppConfig]
  val Conditions = new Conditions(conf)

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(fakeRequest.headers)

  "HasAnyBusinessEnrolment" should {

    "have an audit type specified" in {
      Conditions.hasAnyBusinessEnrolment.routingReason.key shouldBe "has-business-enrolments"
    }

    val scenarios =
      Table(
        ("scenario", "enrolments", "expectedResult"),
        ("business enrolments", Set("enr1"), true),
        ("no business enrolments", Set.empty[String], false)
      )

    forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>
      s"return $expectedResult when user has $scenario" in {
        val ruleContext = mock[RuleContext]
        when(ruleContext.activeEnrolmentKeys).thenReturn(Future(enrolments))

        val (_, evaluationResult) = Conditions.hasAnyBusinessEnrolment.evaluate(ruleContext).run.futureValue

        evaluationResult shouldBe expectedResult
      }
    }
  }

  "HasSaEnrolments" should {

    "have an audit type specified" in {
      Conditions.hasSaEnrolments.routingReason.key shouldBe "has-self-assessment-enrolments"
    }

    val scenarios =
      Table(
        ("scenario", "enrolments", "expectedResult"),
        ("user does not have SA enrolments", Set("enr1"), false),
        ("user has SA enrolments", Set("enr3", "enr4"), true)
      )

    forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>
      s"return $expectedResult when $scenario" in {
        lazy val ruleContext = mock[RuleContext]
        when(ruleContext.activeEnrolmentKeys).thenReturn(Future(enrolments))

        val (_, evaluationResult) = Conditions.hasSaEnrolments.evaluate(ruleContext).run.futureValue

        evaluationResult shouldBe expectedResult
      }
    }
  }

  "HasPreviousReturns" should {

    "have an audit type specified" in {
      Conditions.hasPreviousReturns.routingReason.key shouldBe "has-previous-returns"
    }

    val scenarios =
      Table(
        ("scenario", "lastSaReturn", "expectedResult"),
        ("has previous returns", SaReturn(), true),
        ("has no previous returns", SaReturn.empty, false)
      )

    forAll(scenarios) { (scenario: String, lastSaReturn: SaReturn, expectedResult: Boolean) =>
      s"be true whether the user has any self assessment enrolment - scenario: $scenario" in {

        val ruleContext = mock[RuleContext]
        when(ruleContext.lastSaReturn).thenReturn(Future(lastSaReturn))

        val (_, evaluationResult) = Conditions.hasPreviousReturns.evaluate(ruleContext).run.futureValue
        evaluationResult shouldBe expectedResult
      }
    }
  }

  "IsInAPartnership" should {

    "have an audit type specified" in {
      Conditions.isInAPartnership.routingReason.key shouldBe "is-in-a-partnership"
    }

    val scenarios =
      Table(
        ("scenario", "lastSaReturn", "expectedResult"),
        ("is in a partnership", SaReturn(supplementarySchedules = List("partnership")), true),
        ("is not in a partnership", SaReturn.empty, false)
      )

    forAll(scenarios) { (scenario: String, lastSaReturn: SaReturn, expectedResult: Boolean) =>

      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(fakeRequest.headers)

      s"be true whether the user has a partnership supplementary schedule - scenario: $scenario" in {

        val ruleContext = mock[RuleContext]
        when(ruleContext.lastSaReturn).thenReturn(Future(lastSaReturn))

        val (_, evaluationResult) = Conditions.isInAPartnership.evaluate(ruleContext).run.futureValue
        evaluationResult shouldBe expectedResult
      }
    }
  }

  "IsSelfEmployed" should {

    "have an audit type specified" in {
      Conditions.isSelfEmployed.routingReason.key shouldBe "is-self-employed"
    }

    val scenarios =
      Table(
        ("scenario", "lastSaReturn", "expectedResult"),
        ("is self employed", SaReturn(supplementarySchedules = List("self_employment")), true),
        ("is not self employed", SaReturn.empty, false)
      )

    forAll(scenarios) { (scenario: String, lastSaReturn: SaReturn, expectedResult: Boolean) =>

      s"be true whether the user has a self employment supplementary schedule - scenario: $scenario" in {

        val ruleContext = mock[RuleContext]
        when(ruleContext.lastSaReturn).thenReturn(Future(lastSaReturn))

        val (_, evaluationResult) = Conditions.isSelfEmployed.evaluate(ruleContext).run.futureValue
        evaluationResult shouldBe expectedResult
      }
    }
  }

  "LoggedInViaVerify" should {

    "have an audit type specified" in {
      Conditions.loggedInViaVerify.routingReason.key shouldBe "is-a-verify-user"
    }

    val scenarios =
      Table(
        ("scenario", "authClientDefinedAsVerify", "expectedResult"),
        ("authClient defined as verify", true, true),
        ("authClient not defined as verify", false, false)
      )

    forAll(scenarios) { (scenario: String, authClientDefinedAsVerify: Boolean,  expectedResult: Boolean) =>

      s"be true whether the user has logged in using Verify - scenario: $scenario" in {

        val ruleContext = mock[RuleContext]
        when(ruleContext.isVerifyUser).thenReturn(Future.successful(authClientDefinedAsVerify))

        val (_, evaluationResult) = await(Conditions.loggedInViaVerify.evaluate(ruleContext).run)
        evaluationResult shouldBe expectedResult
      }
    }
  }

  "IsAGovernmentGatewayUser" should {

    "have an audit type specified" in {
      Conditions.isAGovernmentGatewayUser.routingReason.key shouldBe "is-a-government-gateway-user"
    }

    val scenarios =
      Table(
        ("scenario", "tokenPresent", "authClientDefinedAsGG", "expectedResult"),
        ("has logged in using GG", true, true, true),
        ("has not logged in using GG but authClient defined as GG", false, true, true),
        ("has not logged in using GG and authClient not defined as GG", false, false, false)
      )

    forAll(scenarios) { (scenario: String, tokenPresent: Boolean, authClientDefinedAsGG: Boolean, expectedResult: Boolean) =>

      s"be true whether the user has logged in using GG - scenario: $scenario" in {

        implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = if (tokenPresent) {
          FakeRequest().withSession(("token", "token"))
        } else {
          FakeRequest()
        }

        val ruleContext = mock[RuleContext]
        when(ruleContext.isGovernmentGatewayUser).thenReturn(Future.successful(authClientDefinedAsGG))

        val (_, evaluationResult) = Conditions.isAGovernmentGatewayUser.evaluate(ruleContext).run.futureValue
        evaluationResult shouldBe expectedResult
      }
    }
  }

  "HasNino" should {

    "have an audit type specified" in {
      Conditions.hasNino.routingReason.key shouldBe "has-nino"
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
        when(ruleContext.hasNino).thenReturn(Future.successful(ninoPresent))
        val (_, evaluationResult) = Conditions.hasNino.evaluate(ruleContext).run.futureValue
        evaluationResult shouldBe expectedResult
      }
    }
  }

  "GGEnrolmentsAvailable" should {

    "have an audit type specified" in {
      Conditions.ggEnrolmentsAvailable.routingReason.key shouldBe "gg-enrolments-available"
    }

    val scenarios =
      Table(
        ("scenario", "ggEnrolmentsAvailable"),
        ("be true when GG is available", true),
        ("be false GG is not available", false)
      )

    forAll(scenarios) { (scenario: String, ggEnrolmentsAvailable: Boolean) =>

      scenario in {
        val ruleContext = mock[RuleContext]

        val expectedResult = if (ggEnrolmentsAvailable) {
          Future.successful(Enrolments(Set.empty[Enrolment]))
        } else {
          Future.failed(new RuntimeException())
        }

        when(ruleContext.enrolments).thenReturn(expectedResult)

        val (_, evaluationResult) = Conditions.ggEnrolmentsAvailable.evaluate(ruleContext).run.futureValue

        evaluationResult shouldBe ggEnrolmentsAvailable
        verify(ruleContext).enrolments
        verifyNoMoreInteractions(ruleContext)
      }
    }
  }

  "AffinityGroupNotAvailable" should {

    "have an audit type specified" in {
      Conditions.affinityGroupAvailable.routingReason.key shouldBe "affinity-group-available"
    }

    val scenarios =
      Table(
        ("scenario", "affinityGroupAvailable"),
        ("be true when affinity group is available", true),
        ("be false when affinity group is not available", false)
      )

    forAll(scenarios) { (scenario: String, affinityGroupAvailable: Boolean) =>

      scenario in {
        val ruleContext = mock[RuleContext]

        val expectedResult = if (affinityGroupAvailable) {
          Future.successful("some-affinity-group")
        } else {
          Future.failed(new RuntimeException())
        }
        when(ruleContext.affinityGroup).thenReturn(expectedResult)

        val (_, evaluationResult) = Conditions.affinityGroupAvailable.evaluate(ruleContext).run.futureValue

        evaluationResult shouldBe affinityGroupAvailable
        verify(ruleContext).affinityGroup
        verifyNoMoreInteractions(ruleContext)
      }
    }
  }

  "SAReturnAvailable" should {

    "have an audit type specified" in {
      Conditions.saReturnAvailable.routingReason.key shouldBe "sa-return-available"
    }

    val scenarios =
      Table(
        ("scenario", "eventualSaReturn", "expectedResult"),
        ("be true when SA is available", Future.successful(mock[SaReturn]), true),
        ("be false SA is not available", Future.failed(new RuntimeException()), false)
      )

    forAll(scenarios) { (scenario: String, eventualSaReturn: Future[SaReturn], expectedResult: Boolean) =>

      scenario in {

        val ruleContext = mock[RuleContext]
        when(ruleContext.lastSaReturn).thenReturn(eventualSaReturn)

        val (_, evaluationResult) = Conditions.saReturnAvailable.evaluate(ruleContext).run.futureValue

        evaluationResult shouldBe expectedResult
        verify(ruleContext).lastSaReturn
        verifyNoMoreInteractions(ruleContext)
      }
    }
  }

  "HasIndividualAffinityGroup" should {

    "have an audit type specified" in {
      Conditions.hasIndividualAffinityGroup.routingReason.key shouldBe "has-individual-affinity-group"
    }

    val scenarios =
      Table(
        ("scenario", "affinityGroup", "expectedResult"),
        ("return true when affinity group is 'Individual'", AffinityGroupValue.INDIVIDUAL, true),
        ("return false when affinity group is 'Organisation'", AffinityGroupValue.ORGANISATION, false)
      )

    forAll(scenarios) { (scenario: String, affinityGroup: String, expectedResult: Boolean) =>
      scenario in {
        lazy val ruleContext = mock[RuleContext]
        when(ruleContext.affinityGroup).thenReturn(Future(affinityGroup))

        val (_, evaluationResult) = Conditions.hasIndividualAffinityGroup.evaluate(ruleContext).run.futureValue

        evaluationResult shouldBe expectedResult
      }
    }
  }

  "HasAnyInactiveEnrolment" should {

    "have an audit type specified" in {
      Conditions.hasAnyInactiveEnrolment.routingReason.key shouldBe "has-any-inactive-enrolment"
    }

    val scenarios =
      Table(
        ("scenario", "enrolments", "expectedResult"),
        ("return false given no inactive enrolments", Set.empty[String], false),
        ("return true given at least one inactive enrolment", Set("enr1"), true)
      )

    forAll(scenarios) { (scenario: String, enrolments: Set[String], expectedResult: Boolean) =>
      scenario in {
        lazy val ruleContext = mock[RuleContext]
        when(ruleContext.notActivatedEnrolmentKeys).thenReturn(Future.successful(enrolments))

        val (_, evaluationResult) = Conditions.hasAnyInactiveEnrolment.evaluate(ruleContext).run.futureValue

        evaluationResult shouldBe expectedResult
      }
    }
  }
}
