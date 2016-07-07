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
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class TwoStepVerificationServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication with SpecHelpers {

  override lazy val fakeApplication = new FakeApplication(additionalConfiguration = Map("self-assessment-enrolments" -> "some-enrolment"))

  sealed class Setup(twoStepVerificationSwitch: Boolean = true) {
    implicit val request = FakeRequest()
    implicit val hc = HeaderCarrier()
    val auditContext = mock[TAuditContext]
    val principal = Principal(None, Accounts())
    val ruleContext = mock[RuleContext]
    val twoStepVerificationThrottleMock = mock[TwoStepVerificationThrottle]
    val allMocks = Seq(auditContext, twoStepVerificationThrottleMock, ruleContext)
    val businessTaxAccountUrl = "http://localhost:9020/business-account"
    val signOutUrl = "http://localhost:9025/sign-out"

    val twoStepVerification = new TwoStepVerification {
      override def twoStepVerificationPath = ???

      override def twoStepVerificationHost = ???

      override def twoStepVerificationEnabled = twoStepVerificationSwitch

      override def twoStepVerificationThrottle = twoStepVerificationThrottleMock
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

      when(ruleContext.enrolments).thenReturn(Future.successful(Seq.empty[GovernmentGatewayEnrolment]))
      when(ruleContext.activeEnrolments).thenReturn(Future.successful(Set.empty[String]))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext).enrolments
      verify(ruleContext).activeEnrolments
      verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
      verifyZeroInteractions(allMocks: _*)
    }

    "not rewrite the location when credential strength is strong and continue is BTA" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.Strong, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
      verifyZeroInteractions(allMocks: _*)
    }

    "not rewrite the location when continue is BTA and user is registered for 2SV with SA enrolment" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(Some("1234"), "", "")))
      when(ruleContext.activeEnrolments).thenReturn(Future.successful(Set("IR-SA")))
      when(ruleContext.enrolments).thenReturn(Future.successful(Seq.empty[GovernmentGatewayEnrolment]))
      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext, times(2)).activeEnrolments
      verify(ruleContext).enrolments
      verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
      verifyNoMoreInteractions(allMocks: _*)
    }

    "not rewrite the location when continue is BTA and the call to get enrolments is failing" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(Some("1234"), "", "")))
      when(ruleContext.enrolments).thenReturn(Future.failed(new InternalServerException("GG returns 500")))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext).enrolments
      verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
      verifyNoMoreInteractions(allMocks: _*)
    }

    "not rewrite the location when continue is BTA and user is registered for 2SV with more than one enrolment" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(Some("1234"), "", "")))
      when(ruleContext.enrolments).thenReturn(Future.successful(Seq.empty[GovernmentGatewayEnrolment]))
      when(ruleContext.activeEnrolments).thenReturn(Future.successful(Set("IR-SA", "some-other-enrolment")))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext).enrolments
      verify(ruleContext).activeEnrolments
      verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
      verifyNoMoreInteractions(allMocks: _*)
    }

    "rewrite the location to have the 2SV url and the origin when the continue url is BTA, the user is not registered for 2SV, no strong credentials, has one enrolment, has SA enrolment and throttle chooses optional registration" in new Setup {

      val userid = "userId"
      val loggedInUser = LoggedInUser(userid, None, None, None, CredentialStrength.Weak, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(None, "", "")))
      when(ruleContext.activeEnrolments).thenReturn(Future.successful(Set("some-enrolment")))
      when(ruleContext.enrolments).thenReturn(Future.successful(Seq.empty[GovernmentGatewayEnrolment]))
      when(twoStepVerificationThrottleMock.registrationMandatory(userid)).thenReturn(Future.successful(false))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe Some(Locations.twoStepVerification(Map("continue" -> businessTaxAccountUrl, "failure" -> businessTaxAccountUrl, "origin" -> "business-tax-account")))
      verify(ruleContext).currentCoAFEAuthority
      verify(ruleContext, times(2)).activeEnrolments
      verify(ruleContext).enrolments
      verify(twoStepVerificationThrottleMock).registrationMandatory(userid)
      verify(auditContext, atLeastOnce()).setRoutingReason(any[RoutingReason.RoutingReason], anyBoolean())(any[ExecutionContext])
      verify(auditContext).setSentTo2SVRegister(true)
      verifyNoMoreInteractions(allMocks: _*)
    }
  }
}