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
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class TwoStepVerificationSpec extends UnitSpec with MockitoSugar with WithFakeApplication with SpecHelpers {

  "TwoStepVerification" should {

    "not rewrite the location when two step verification is disabled" in {

      val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
      val accounts = Accounts(paye = Some(PayeAccount("", Nino("CS100700A"))))
      val principal = Principal(None, accounts)
      implicit val authContext = AuthContext(loggedInUser, principal, None)

      implicit val request = FakeRequest()
      implicit val hc = HeaderCarrier()

      val auditContext = mock[TAuditContext]

      val twoStepVerification = new TwoStepVerification {
        override def twoStepVerificationPath = ???

        override def twoStepVerificationHost = ???

        override def twoStepVerificationEnabled = false
      }

      val ruleContext = mock[RuleContext]
      val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

      result shouldBe None

      verifyZeroInteractions(ruleContext)
    }
  }

  it should {

    val scenarios = Table(
      ("scenario", "continueUrl"),
      ("continue is PTA", evaluateUsingPlay(PersonalTaxAccount)),
      ("continue is BTA", evaluateUsingPlay(BusinessTaxAccount))
    )

    forAll(scenarios) { (scenario: String, continueUrl: Location) =>

      s"not rewrite the location when neither NINO nor SAUTR - scenario: $scenario" in {

        val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
        val principal = Principal(None, Accounts())
        implicit val authContext = AuthContext(loggedInUser, principal, None)

        implicit val request = FakeRequest()
        implicit val hc = HeaderCarrier()

        val auditContext = mock[TAuditContext]

        val twoStepVerification = new TwoStepVerification {
          override def twoStepVerificationPath = ???

          override def twoStepVerificationHost = ???

          override def twoStepVerificationEnabled = true
        }

        val ruleContext = mock[RuleContext]
        val result = await(twoStepVerification.getDestinationVia2SV(continueUrl, ruleContext, auditContext))

        result shouldBe None

        verifyZeroInteractions(ruleContext)
      }
    }

    forAll(scenarios) { (scenario: String, continueUrl: Location) =>

      s"not rewrite the location when credential strength is strong (this can happen when 2SV is disabled in Company Auth) - scenario: $scenario" in {

        val credentialStrength = CredentialStrength.Strong
        val loggedInUser = LoggedInUser("userId", None, None, None, credentialStrength, ConfidenceLevel.L0)
        val principal = Principal(None, Accounts())
        implicit val authContext = AuthContext(loggedInUser, principal, None)

        implicit val request = FakeRequest()
        implicit val hc = HeaderCarrier()

        val auditContext = mock[TAuditContext]

        val twoStepVerification = new TwoStepVerification {
          override def twoStepVerificationPath = ???

          override def twoStepVerificationHost = ???

          override def twoStepVerificationEnabled = true
        }

        val ruleContext = mock[RuleContext]
        val result = await(twoStepVerification.getDestinationVia2SV(continueUrl, ruleContext, auditContext))

        result shouldBe None

        verifyZeroInteractions(ruleContext)
      }
    }
  }

  it should {

    val scenarios = Table(
      ("scenario", "hasNino", "hasSaUtr"),
      ("continue is BTA with NINO", Some(true), None),
      ("continue is BTA with SAUTR", None, Some(true))
    )

    forAll(scenarios) { (scenario: String, hasNino: Option[Boolean], hasSaUtr: Option[Boolean]) =>

      s"not rewrite the location when user is registered for 2SV - scenario: $scenario" in {

        val loggedInUser = LoggedInUser("userId", None, None, None, CredentialStrength.None, ConfidenceLevel.L0)
        val accounts = Accounts(
          paye = hasNino.collect { case true => PayeAccount("", Nino("CS100700A")) },
          sa = hasSaUtr.collect { case true => SaAccount("", SaUtr("some-saUtr")) }
        )
        val principal = Principal(None, accounts)
        implicit val authContext = AuthContext(loggedInUser, principal, None)

        implicit val request = FakeRequest()
        implicit val hc = HeaderCarrier()

        val auditContext = mock[TAuditContext]

        val twoStepVerification = new TwoStepVerification {
          override def twoStepVerificationPath = ???

          override def twoStepVerificationHost = ???

          override def twoStepVerificationEnabled = true
        }

        val ruleContext = mock[RuleContext]
        when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(Some("1234"))))
        val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

        result shouldBe None
        verify(ruleContext).currentCoAFEAuthority
      }
    }
  }

  it should {
    val scenarios = Table(
      ("scenario", "hasNino", "hasSaUtr"),
      ("continue is BTA with SAUTR", None, Some(true)),
      ("continue is BTA with NINO", Some(true), None)
    )

    forAll(scenarios) { (scenario: String, hasNino: Option[Boolean], hasSaUtr: Option[Boolean]) =>

      s"rewrite the location using 2SV url when the continue url is BTA and the user is not registered for 2SV - scenario: $scenario" in {

        val credentialStrength = CredentialStrength.Weak
        val loggedInUser = LoggedInUser("userId", None, None, None, credentialStrength, ConfidenceLevel.L0)
        val accounts = Accounts(
          paye = hasNino.collect { case true => PayeAccount("", Nino("CS100700A")) },
          sa = hasSaUtr.collect { case true => SaAccount("", SaUtr("some-saUtr")) }
        )
        val principal = Principal(None, accounts)
        implicit val authContext = AuthContext(loggedInUser, principal, None)

        implicit val request = FakeRequest()
        implicit val hc = HeaderCarrier()

        val auditContext = mock[TAuditContext]

        val continueUrl = "http://localhost:9020/business-account"

        val twoStepVerification = new TwoStepVerification {
          override def twoStepVerificationPath = "/register"

          override def twoStepVerificationHost = "http://some-host"

          override def twoStepVerificationEnabled = true
        }

        val ruleContext = mock[RuleContext]
        when(ruleContext.currentCoAFEAuthority).thenReturn(Future.successful(CoAFEAuthority(None)))
        val result = await(twoStepVerification.getDestinationVia2SV(BusinessTaxAccount, ruleContext, auditContext))

        result shouldBe Some(Locations.TwoStepVerification("continue" -> continueUrl, "failure" -> continueUrl))

        verify(ruleContext).currentCoAFEAuthority
      }
    }
  }
}