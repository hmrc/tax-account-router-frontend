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

package controllers

import helpers.SpecHelpers
import model.Locations._
import model._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class TwoStepVerificationSpec extends UnitSpec with MockitoSugar with WithFakeApplication with SpecHelpers {

  override lazy val fakeApplication = new FakeApplication(additionalConfiguration = Map("self-assessment-enrolments" -> "some-enrolment"))

  trait Setup {
    implicit val request = FakeRequest()
    implicit val hc = HeaderCarrier()
    val auditContext = mock[TAuditContext]
    val principal = Principal(None, Accounts())
    val ruleContext = mock[RuleContext]
  }

  "getDestinationVia2SV" should {

    "not rewrite the location when two step verification is disabled" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      val twoStepVerification = new TwoStepVerification {
        override def twoStepVerificationPath = ???

        override def twoStepVerificationHost = ???

        override def twoStepVerificationEnabled = false
      }

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None

      verifyZeroInteractions(ruleContext)
    }

    "not rewrite the location when no enrolments and continue is PTA" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      val twoStepVerification = new TwoStepVerification {
        override def twoStepVerificationPath = ???

        override def twoStepVerificationHost = ???

        override def twoStepVerificationEnabled = true
      }

      val result = await(twoStepVerification.getDestinationVia2SV(PersonalTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verifyZeroInteractions(ruleContext)
    }

    "not rewrite the location when no enrolments and continue is BTA" in new Setup {
      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      val twoStepVerification = new TwoStepVerification {
        override def twoStepVerificationPath = ???

        override def twoStepVerificationHost = ???

        override def twoStepVerificationEnabled = true
      }

      when(ruleContext.activeEnrolments).thenReturn(Future.successful(Set.empty[String]))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext, times(2)).activeEnrolments
      verifyNoMoreInteractions(ruleContext)
    }

    "not rewrite the location when credential strength is strong (this can happen when 2SV is disabled in Company Auth) and continue is PTA" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.Strong, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      val twoStepVerification = new TwoStepVerification {
        override def twoStepVerificationPath = ???

        override def twoStepVerificationHost = ???

        override def twoStepVerificationEnabled = true
      }

      val result = await(twoStepVerification.getDestinationVia2SV(PersonalTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verifyZeroInteractions(ruleContext)
    }

    "not rewrite the location when credential strength is strong (this can happen when 2SV is disabled in Company Auth) and continue is BTA" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.Strong, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      val twoStepVerification = new TwoStepVerification {
        override def twoStepVerificationPath = ???

        override def twoStepVerificationHost = ???

        override def twoStepVerificationEnabled = true
      }

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verifyZeroInteractions(ruleContext)
    }

    "not rewrite the location when continue is BTA and user is registered for 2SV with SA enrolment" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      val twoStepVerification = new TwoStepVerification {
        override def twoStepVerificationPath = ???

        override def twoStepVerificationHost = ???

        override def twoStepVerificationEnabled = true
      }

      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(Some("1234"))))
      when(ruleContext.activeEnrolments).thenReturn(Future.successful(Set("IR-SA")))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext, times(2)).activeEnrolments
      verifyNoMoreInteractions(ruleContext)
    }

    "rewrite the location using 2SV url when the continue url is BTA and the user is not registered for 2SV with SA enrolment" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.Weak, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      val continueUrl = "http://localhost:9020/business-account"

      val twoStepVerification = new TwoStepVerification {
        override def twoStepVerificationPath = "/register"

        override def twoStepVerificationHost = "http://some-host"

        override def twoStepVerificationEnabled = true
      }

      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(None)))
      when(ruleContext.activeEnrolments).thenReturn(Future.successful(Set("some-enrolment")))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe Some(Locations.TwoStepVerification("continue" -> continueUrl, "failure" -> continueUrl))
      verify(ruleContext).currentCoAFEAuthority
      verify(ruleContext, times(2)).activeEnrolments
      verifyNoMoreInteractions(ruleContext)
    }

    "not rewrite the location when continue is BTA and GG returns 500" in new Setup {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      val twoStepVerification = new TwoStepVerification {
        override def twoStepVerificationPath = ???

        override def twoStepVerificationHost = ???

        override def twoStepVerificationEnabled = true
      }

      when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(Some("1234"))))
      when(ruleContext.activeEnrolments).thenReturn(Future.failed(new InternalServerException("GG returns 500")))

      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None
      verify(ruleContext).activeEnrolments
      verifyNoMoreInteractions(ruleContext)
    }
  }
}