/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers.internal

import cats.data.WriterT
import connector._
import controllers.internal.AccountTypeResponse.accountTypeReads
import engine.{AuditInfo, RuleEngine}
import helpers.VerifyLogger
import model.{Location, Locations, RuleContext}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import support.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.connectors.domain.CredentialStrength
import uk.gov.hmrc.play.frontend.filters.MicroserviceFilterSupport

import scala.concurrent.Future

class AccountTypeControllerSpec extends UnitSpec with MockitoSugar with OneAppPerSuite with Eventually with MicroserviceFilterSupport {

  "Account type controller " should {

    "return type Organisation when BTA location is provided by rules and there is an origin for this location" in new Setup {
      // given
      val engineResult = WriterT(Future.successful((emptyAuditInfo, Locations.BusinessTaxAccount)))
      when(mockRuleEngine.getLocation(mockRuleContext)).thenReturn(engineResult)
      when(mockRuleContext.affinityGroup).thenReturn(Future.successful(AffinityGroupValue.ORGANISATION))
      when(mockAuthConnector.userAuthority(anyString())(any(), any())).thenReturn(Future.successful(expectedAuthority))
      when(mockAuthConnector.getEnrolments(anyString())(any(), any())).thenReturn(Future.successful(expectedActiveEnrolmentsSeq()))
      when(mockUserDetailsConnector.getUserDetails(anyString())(any(), any())).thenReturn(Future.successful(expectedUserDetailsAsIndividual))

      // when
      val result = await(controller.accountTypeForCredId(credId)(FakeRequest()))

      // then
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "type").as[AccountType.AccountType] shouldBe AccountType.Organisation

      verify(mockRuleEngine).getLocation(mockRuleContext)

      verifyWarningLoggings(
        List(s"[AIV-1396] TAR and MPR agree that login is ${AccountType.Organisation}, TAR applying the rule: No rule applied. MPR applying the rule: org-by-biz-enrolments-rule.",
          "[AIV-1396] the userActiveEnrolments are: Set(some-key, enr1)"), 2)

      verifyNoMoreInteractions(allMocksExceptAuditInfo: _*)
    }

    "return type Individual when PTA location is provided by rules and there is an origin for this location" in new Setup {
      // given
      val engineResult = WriterT(Future.successful((emptyAuditInfo, Locations.PersonalTaxAccount)))
      when(mockRuleEngine.getLocation(mockRuleContext)).thenReturn(engineResult)
      when(mockRuleContext.affinityGroup).thenReturn(Future.successful(AffinityGroupValue.INDIVIDUAL))
      when(mockAuthConnector.userAuthority(anyString())(any(), any())).thenReturn(Future.successful(expectedAuthority))
      when(mockAuthConnector.getEnrolments(anyString())(any(), any())).thenReturn(Future.successful(expectedActiveEnrolmentsSeq(withBusinessEnrolment = false)))
      when(mockUserDetailsConnector.getUserDetails(anyString())(any(), any())).thenReturn(Future.successful(expectedUserDetailsAsIndividual))

      // when
      val result = await(controller.accountTypeForCredId(credId)(FakeRequest()))

      // then
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "type").as[AccountType.AccountType] shouldBe AccountType.Individual

      verify(mockRuleEngine).getLocation(mockRuleContext)

      //verifyWarningLoggings(List(s"[AIV-1349] TAR and 4PR agree that login is ${AccountType.Individual}, TAR applying the rule: No rule applied.", "[AIV-1349] the userActiveEnrolments are: Set(some-key)"), 2)
      verifyWarningLoggings(
        List(
          s"[AIV-1396] TAR and MPR agree that login is ${AccountType.Individual}, TAR applying the rule: No rule applied. MPR applying the rule: individual-rule.",
          "[AIV-1396] the userActiveEnrolments are: Set(some-key)"), 2)

      verifyNoMoreInteractions(allMocksExceptAuditInfo: _*)
    }

    "return default account type when an unknown location is provided by rules (not PTA or BTA)" in new Setup {
      // given
      val unknownLocation = Location("unknown-location", "/unknown-location")
      val engineResult = WriterT(Future.successful((emptyAuditInfo, unknownLocation)))
      when(mockRuleEngine.getLocation(mockRuleContext)).thenReturn(engineResult)
      when(mockRuleContext.affinityGroup).thenReturn(Future.successful(AffinityGroupValue.ORGANISATION))
      when(mockAuthConnector.userAuthority(anyString())(any(), any())).thenReturn(Future.successful(expectedAuthority))
      when(mockAuthConnector.getEnrolments(anyString())(any(), any())).thenReturn(Future.successful(expectedActiveEnrolmentsSeq(withBusinessEnrolment = false)))
      when(mockUserDetailsConnector.getUserDetails(anyString())(any(), any())).thenReturn(Future.successful(expectedUserDetailsAsIndividual))

      // when
      val result = await(controller.accountTypeForCredId(credId)(FakeRequest()))

      // then
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "type").as[AccountType.AccountType] shouldBe theDefaultAccountType

      verify(mockRuleEngine).getLocation(mockRuleContext)

      verifyWarningLoggings(
        List(
          s"Location ${unknownLocation.url} is not recognised as PTA or BTA. Returning default type.",
          s"[AIV-1396] TAR and MPR disagree, TAR applying the rule: $theDefaultAccountType, MPR applying the rule: individual-rule.",
          s"[AIV-1396] the userActiveEnrolments are: Set(some-key)"), 3)

      verifyNoMoreInteractions(allMocksExceptAuditInfo: _*)
    }

    "return agent account type when the affinity group is agent" in new Setup {
      // given
      when(mockRuleContext.affinityGroup).thenReturn(Future.successful(AffinityGroupValue.AGENT))

      // when
      val result = await(controller.accountTypeForCredId(credId)(FakeRequest()))

      // then
      status(result) shouldBe 200

      import AccountTypeResponse.accountTypeReads

      (jsonBodyOf(result) \ "type").as[AccountType.AccountType] shouldBe AccountType.Agent

      verifyNoMoreInteractions(allMocksExceptAuditInfo: _*)
    }

  }

  trait Setup extends VerifyLogger {
    val emptyAuditInfo: AuditInfo                      = AuditInfo.Empty
    val mockAuthConnector: FrontendAuthConnector       = mock[FrontendAuthConnector]
    val mockUserDetailsConnector: UserDetailsConnector = mock[UserDetailsConnector]
    val mockRuleContext: RuleContext                   = mock[RuleContext]
    val mockRuleEngine: RuleEngine                     = mock[RuleEngine]

    implicit val hc:HeaderCarrier = HeaderCarrier()

    val enrolmentsUri = "/enrolments"
    val userDetailsLink = "/userDetailsLink"
    val someIdsUri = "/ids-uri"

    val expectedAuthority =
      UserAuthority(
        twoFactorAuthOtpId = None,
        idsUri = Some(someIdsUri),
        userDetailsUri = Some(userDetailsLink),
        enrolmentsUri = Some(enrolmentsUri),
        credentialStrength = CredentialStrength.None,
        nino = None,
        saUtr = None)

    def expectedActiveEnrolmentsSeq(withBusinessEnrolment: Boolean = true): Seq[GovernmentGatewayEnrolment] = {
      if (withBusinessEnrolment){
        Seq(
          GovernmentGatewayEnrolment("some-key", Seq(EnrolmentIdentifier("key-1", "value-1")), EnrolmentState.ACTIVATED),
          GovernmentGatewayEnrolment("enr1", Seq(EnrolmentIdentifier("enr1", "enr1")), EnrolmentState.ACTIVATED),
          GovernmentGatewayEnrolment("some-other-key", Seq(EnrolmentIdentifier("key-2", "value-2")), EnrolmentState.NOT_YET_ACTIVATED)
        )
      }
      else {
        Seq(
          GovernmentGatewayEnrolment("some-key", Seq(EnrolmentIdentifier("key-1", "value-1")), EnrolmentState.ACTIVATED),
          GovernmentGatewayEnrolment("some-other-key", Seq(EnrolmentIdentifier("key-2", "value-2")), EnrolmentState.NOT_YET_ACTIVATED)
        )
      }
    }

    val expectedAffinityGroup = "Individual"
    val expectedUserDetailsAsIndividual = UserDetails(Some(CredentialRole("User")), expectedAffinityGroup)

    val allMocksExceptAuditInfo = Seq(mockRuleEngine, mockLogger)

    val credId = "credId"

    val theDefaultAccountType = AccountType.Organisation

    val ruleContextCaptor = ArgumentCaptor.forClass(classOf[RuleContext])

    val controller = new AccountTypeController {

      override val ruleEngine = mockRuleEngine

      override val defaultAccountType = theDefaultAccountType

      override val logger = mockLogger

      override def createRuleContext(credId: String)(implicit request: Request[AnyContent], hc: HeaderCarrier): RuleContext = mockRuleContext

      override def authConnector: FrontendAuthConnector = mockAuthConnector

      override def userDetailsConnector: UserDetailsConnector = mockUserDetailsConnector
    }
  }

}
