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

package services

import connector.GovernmentGatewayEnrolment
import helpers.SpecHelpers
import model.Locations._
import model._
import org.mockito.Matchers._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class TwoStepVerificationServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication with SpecHelpers {

  val saEnrolments = Set("sa-enrolments")
  val vatEnrolments = Set("vat-enrolment1", "vat-enrolment2")
  override lazy val fakeApplication = FakeApplication(additionalConfiguration = Map("self-assessment-enrolments" -> saEnrolments, "vat-enrolments" -> vatEnrolments))

  sealed class Setup(twoStepVerificationSwitch: Boolean = true) {
    implicit val request = FakeRequest()
    implicit val hc = HeaderCarrier()
    val auditContext = mock[TAuditContext]
    val principal = Principal(None, Accounts())
    val ruleContext = mock[RuleContext]
    val twoStepVerificationThrottleMock = mock[TwoStepVerificationThrottle]
    val allMocks = Seq(auditContext, twoStepVerificationThrottleMock, ruleContext)
    val signOutUrl = "http://localhost:9025/sign-out"

    val twoStepVerification = new TwoStepVerification {
      override def twoStepVerificationPath = ???

      override def twoStepVerificationHost = ???

      override val twoStepVerificationEnabled = twoStepVerificationSwitch

      override val twoStepVerificationThrottle = twoStepVerificationThrottleMock
    }
  }

  "getDestinationVia2SV" should {

    "not rewrite the location when two step verification is disabled" in new Setup(twoStepVerificationSwitch = false) {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None

      verifyZeroInteractions(allMocks: _*)
    }

    "not rewrite the location when continue is PTA" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      val result = await(twoStepVerification.getDestinationVia2SV(PersonalTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verifyZeroInteractions(allMocks: _*)
    }

    "not rewrite the location when no enrolments and continue is BTA" in new Setup {
      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(None, "", "")))
      when(ruleContext.enrolments).thenReturn(Future.successful(Seq.empty[GovernmentGatewayEnrolment]))
      when(ruleContext.activeEnrolments).thenReturn(Future.successful(Set.empty[String]))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext, times(2)).currentCoAFEAuthority
      verify(ruleContext, times(2)).enrolments
      verify(ruleContext, times(2)).activeEnrolments
      verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
      verifyZeroInteractions(allMocks: _*)
    }

    "not rewrite the location when credential strength is strong and continue is BTA" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.Strong, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)
      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(None, "", "")))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext, times(2)).currentCoAFEAuthority
      verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
      verifyZeroInteractions(allMocks: _*)
    }

    "not rewrite the location when continue is BTA and user is registered for 2SV with SA enrolment" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(Some("1234"), "", "")))
      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext, times(2)).currentCoAFEAuthority
      verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
      verifyNoMoreInteractions(allMocks: _*)
    }

    "not rewrite the location when continue is BTA and the call to get enrolments is failing" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(None, "", "")))
      when(ruleContext.enrolments).thenReturn(Future.failed(new InternalServerException("GG returns 500")))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext, times(2)).enrolments
      verify(ruleContext, times(2)).currentCoAFEAuthority
      verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
      verifyNoMoreInteractions(allMocks: _*)
    }

    "not rewrite the location when continue is BTA and user has more than one enrolment" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(None, "", "")))
      when(ruleContext.enrolments).thenReturn(Future.successful(Seq.empty[GovernmentGatewayEnrolment]))
      when(ruleContext.activeEnrolments).thenReturn(Future.successful(Set("IR-SA", "some-other-enrolment")))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext, times(2)).currentCoAFEAuthority
      verify(ruleContext, times(2)).enrolments
      verify(ruleContext, times(2)).activeEnrolments
      verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
      verifyNoMoreInteractions(allMocks: _*)
    }

    val scenarios = Table(
      ("scenario", "isMandatory", "isAdmin", "enrolments", "expectedRuleName", "expectedRedirectLocation"),
      ("redirect to 2SV when admin user has only SA enrolment, throttle chooses optional ",
        false, true, saEnrolments, "sa", () => locationFromConf("two-step-verification-optional")),
      ("redirect to 2SV when admin user has only SA enrolment,throttle chooses mandatory",
        true, true, saEnrolments, "sa", () => locationFromConf("two-step-verification-mandatory")),
      ("redirect to 2SV when assistant user has only SA enrolment, throttle chooses optional",
        false, false, saEnrolments, "sa", () => locationFromConf("two-step-verification-optional")),
      ("redirect to 2SV when assistant user has only SA enrolment, throttle chooses mandatory",
        true, false, saEnrolments, "sa", () => locationFromConf("two-step-verification-mandatory")),
      ("redirect to set up extra security when admin user has only SA and VAT enrolments, throttle chooses optional", false, true, saEnrolments ++ vatEnrolments, "sa_vat", () => locationFromConf("set-up-extra-security")),
      ("redirect to set up extra security when admin user has only SA and VAT enrolments, throttle chooses mandatory", true, true, saEnrolments ++ vatEnrolments, "sa_vat", () => locationFromConf("set-up-extra-security")),
      ("redirect to bta when assistant user has only SA and VAT enrolments, throttle chooses optional", false, false, saEnrolments ++ vatEnrolments, "sa_vat", () => locationFromConf("bta")),
      ("redirect to bta when assistant user has only SA and VAT enrolments, throttle chooses mandatory", true, false, saEnrolments ++ vatEnrolments, "sa_vat", () => locationFromConf("bta"))
    )

    forAll(scenarios) { (scenario: String, isMandatory: Boolean, isAdmin: Boolean, enrolments: Set[String], expectedRuleName: String, expectedRedirectLocation: () => Location) =>
      s"$scenario, continue url is BTA, the user is not registered for 2SV, no strong credentials" in new Setup {
        val userId = "userId"
        val loggedInUser = LoggedInUser(userId, None, None, None, CredentialStrength.Weak, ConfidenceLevel.L0)
        implicit val authContext = AuthContext(loggedInUser, principal, None)

        when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(None, "", "")))
        when(ruleContext.activeEnrolments).thenReturn(Future.successful(enrolments))
        when(ruleContext.isAdmin).thenReturn(Future.successful(isAdmin))
        when(ruleContext.enrolments).thenReturn(Future.successful(Seq.empty[GovernmentGatewayEnrolment]))
        when(twoStepVerificationThrottleMock.registrationMandatory(expectedRuleName, userId)).thenReturn(Future.successful(isMandatory))

        val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

        result shouldBe Some(expectedRedirectLocation())
        verify(ruleContext, atLeastOnce()).currentCoAFEAuthority
        verify(ruleContext, Mockito.atMost(2)).activeEnrolments
        verify(ruleContext).isAdmin
        verify(ruleContext, Mockito.atMost(2)).enrolments
        verify(twoStepVerificationThrottleMock).registrationMandatory(expectedRuleName, userId)
        verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
        if (isMandatory) {
          verify(auditContext).setSentToMandatory2SVRegister(expectedRuleName)
        } else {
          verify(auditContext).setSentToOptional2SVRegister(expectedRuleName)
        }
        verifyNoMoreInteractions(allMocks: _*)
      }
    }
  }
}