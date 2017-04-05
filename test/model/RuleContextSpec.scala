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

package model

import ch.qos.logback.classic.Level
import connector._
import org.mockito.Mockito._
import org.scalatest.LoneElement
import org.scalatest.mock.MockitoSugar
import play.api.Logger
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain.CredentialStrength
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RuleContextSpec extends UnitSpec with MockitoSugar with WithFakeApplication with LogCapturing with LoneElement {

  sealed trait Setup {

    implicit val hc = HeaderCarrier()
    implicit val request = FakeRequest()
    val mockAuthConnector: FrontendAuthConnector = mock[FrontendAuthConnector]
    val mockUserDetailsConnector = mock[UserDetailsConnector]
    val mockSelfAssessmentConnector = mock[SelfAssessmentConnector]

    val allMocks = Seq(mockAuthConnector, mockUserDetailsConnector, mockSelfAssessmentConnector)
    val credId = "credId"

    val ruleContextWithCredId = new RuleContext(Some(credId)) {
      override val authConnector = mockAuthConnector
      override val userDetailsConnector = mockUserDetailsConnector
      override val selfAssessmentConnector = mockSelfAssessmentConnector
    }

    val ruleContextWithNoCredId = new RuleContext(None) {
      override val authConnector = mockAuthConnector
      override val userDetailsConnector = mockUserDetailsConnector
      override val selfAssessmentConnector = mockSelfAssessmentConnector
    }

    val enrolmentsUri = "/enrolments"
    val userDetailsLink = "/userDetailsLink"
    val someIdsUri = "/ids-uri"
    val internalUserIdentifier = InternalUserIdentifier("user-id")
    val expectedAuthority = UserAuthority(twoFactorAuthOtpId = None, idsUri = Some(someIdsUri), userDetailsUri = Some(userDetailsLink), enrolmentsUri = Some(enrolmentsUri),
      credentialStrength = CredentialStrength.None,
      nino = None,
      saUtr = None)

    val expectedAffinityGroup = "some-affinity-group"
    val expectedUserDetails = UserDetails(Some(CredentialRole("User")), expectedAffinityGroup)

    val expectedActiveEnrolmentsSeq = Seq(
      GovernmentGatewayEnrolment("some-key", Seq(EnrolmentIdentifier("key-1", "value-1")), EnrolmentState.ACTIVATED),
      GovernmentGatewayEnrolment("some-other-key", Seq(EnrolmentIdentifier("key-2", "value-2")), EnrolmentState.NOT_YET_ACTIVATED)
    )

    when(mockUserDetailsConnector.getUserDetails(userDetailsLink)).thenReturn(Future.successful(expectedUserDetails))
    when(mockAuthConnector.getEnrolments(enrolmentsUri)).thenReturn(Future.successful(expectedActiveEnrolmentsSeq))
    when(mockAuthConnector.userAuthority(credId)).thenReturn(Future.successful(expectedAuthority))
    when(mockAuthConnector.currentUserAuthority).thenReturn(Future.successful(expectedAuthority))
    when(mockAuthConnector.getIds(someIdsUri)).thenReturn(Future.successful(internalUserIdentifier))
  }

  "activeEnrolments" should {
    "return active enrolments when available" in new Setup {
      //given
      val expectedActiveEnrolmentsSet = Set("some-key")

      //when
      val returnedActiveEnrolments = await(ruleContextWithCredId.activeEnrolmentKeys)

      //then
      expectedActiveEnrolmentsSet shouldBe returnedActiveEnrolments

      //and
      verify(mockAuthConnector).userAuthority(credId)
      verify(mockAuthConnector).getEnrolments(enrolmentsUri)
      verifyNoMoreInteractions(allMocks: _*)
    }
  }

  "notActivatedEnrolments" should {
    "return not activated enrolments when available" in new Setup {
      //given
      val expectedActiveEnrolmentsSet = Set("some-other-key")

      //when
      val returnedActiveEnrolments = await(ruleContextWithCredId.notActivatedEnrolmentKeys)

      //then
      expectedActiveEnrolmentsSet shouldBe returnedActiveEnrolments

      //and
      verify(mockAuthConnector).userAuthority(credId)
      verify(mockAuthConnector).getEnrolments(enrolmentsUri)
      verifyNoMoreInteractions(allMocks: _*)
    }
  }

  "saUserInfo" should {
    "return the last self-assessment return if available in SA and the user has an SA account" in new Setup {
      //given
      val saReturn = SaReturn(List("something"))

      val expectedUtr = "123456789"
      when(mockSelfAssessmentConnector.lastReturn(expectedUtr)(hc)).thenReturn(Future(saReturn))

      val authorityWithSa = UserAuthority(twoFactorAuthOtpId = None, idsUri = Some(""), userDetailsUri = Some(userDetailsLink), enrolmentsUri = Some(enrolmentsUri),
        credentialStrength = CredentialStrength.None,
        nino = None,
        saUtr = Some(SaUtr(expectedUtr)))
      when(mockAuthConnector.userAuthority(credId)).thenReturn(Future.successful(authorityWithSa))

      //then
      await(ruleContextWithCredId.lastSaReturn) shouldBe saReturn
      verify(mockSelfAssessmentConnector).lastReturn(expectedUtr)
    }

    "return an empty self-assessment return if the user has no SA account" in new Setup {
      val authorityWithoutSa = UserAuthority(twoFactorAuthOtpId = None, idsUri = Some(""), userDetailsUri = Some(userDetailsLink), enrolmentsUri = Some(enrolmentsUri),
        credentialStrength = CredentialStrength.None,
        nino = None,
        saUtr = None)
      when(mockAuthConnector.userAuthority(credId)).thenReturn(Future.successful(authorityWithoutSa))

      await(ruleContextWithCredId.lastSaReturn) shouldBe SaReturn.empty

      verifyZeroInteractions(mockSelfAssessmentConnector)
    }
  }

  "authority" should {
    "return the authority for credId if rule context is created with credId" in new Setup {
      val result = ruleContextWithCredId.authority

      await(result) shouldBe expectedAuthority
      verify(mockAuthConnector).userAuthority(credId)
      verifyNoMoreInteractions(allMocks: _*)
    }

    "return the current authorityif rule context is created with no credId" in new Setup {
      val result = ruleContextWithNoCredId.authority

      await(result) shouldBe expectedAuthority
      verify(mockAuthConnector).currentUserAuthority
      verifyNoMoreInteractions(allMocks: _*)
    }
  }

  "affinityGroup" should {
    "return the affinity group from gg profile" in new Setup {

      val result = await(ruleContextWithCredId.affinityGroup)

      result shouldBe expectedAffinityGroup

      verify(mockAuthConnector).userAuthority(credId)
      verify(mockUserDetailsConnector).getUserDetails(userDetailsLink)
      verifyNoMoreInteractions(allMocks: _*)
    }
  }

  "userDetails" should {
    "return future failed and log a warning message when userDetailsLink is empty" in new Setup {
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        reset(mockAuthConnector)
        val authorityWithNoUserDetailsLink = expectedAuthority.copy(userDetailsUri = None)
        when(mockAuthConnector.userAuthority(credId)).thenReturn(Future.successful(authorityWithNoUserDetailsLink))

        val expectedException = intercept[RuntimeException] {
          await(ruleContextWithCredId.userDetails)
        }
        expectedException.getMessage shouldBe "userDetailsUri is not defined"

        verify(mockAuthConnector).userAuthority(credId)

        val loggedEvent = logEvents.loneElement
        loggedEvent.getMessage shouldBe "failed to get user details because userDetailsUri is not defined"
        loggedEvent.getLevel shouldBe Level.WARN

        verifyNoMoreInteractions(allMocks: _*)
      }
    }
  }
}
