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

package model

import connector._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RuleContextSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  sealed trait Setup {

    implicit val hc = HeaderCarrier()
    val mockFrontendAuthConnector = mock[FrontendAuthConnector]
    val mockUserDetailsConnector = mock[UserDetailsConnector]
    val mockSelfAssessmentConnector = mock[SelfAssessmentConnector]
    val allMocks = Seq(mockFrontendAuthConnector, mockUserDetailsConnector, mockSelfAssessmentConnector)

    val ruleContext = new RuleContext(mock[AuthContext]) {
      override val frontendAuthConnector = mockFrontendAuthConnector
      override val userDetailsConnector = mockUserDetailsConnector
      override val selfAssessmentConnector = mockSelfAssessmentConnector
    }

    val enrolmentsUri = "/enrolments"
    val userDetailsLink = "/userDetailsLink"
    val credentialId = "cred id"
    val expectedCoafeAuthority = CoAFEAuthority(None, enrolmentsUri = enrolmentsUri, userDetailsLink = userDetailsLink, credentialId = credentialId)

    val expectedAffinityGroup = "some-affinity-group"
    val expectedUserDetails = UserDetails(Some(CredentialRole("User")), expectedAffinityGroup)

    val expectedActiveEnrolmentsSeq = Seq(
      GovernmentGatewayEnrolment("some-key", Seq(EnrolmentIdentifier("key-1", "value-1")), EnrolmentState.ACTIVATED),
      GovernmentGatewayEnrolment("some-other-key", Seq(EnrolmentIdentifier("key-2", "value-2")), EnrolmentState.NOT_YET_ACTIVATED)
    )

    when(mockUserDetailsConnector.getUserDetails(userDetailsLink)).thenReturn(Future.successful(expectedUserDetails))
    when(mockFrontendAuthConnector.getEnrolments(enrolmentsUri)).thenReturn(Future.successful(expectedActiveEnrolmentsSeq))
    when(mockFrontendAuthConnector.currentCoAFEAuthority()).thenReturn(Future.successful(expectedCoafeAuthority))
  }

  "activeEnrolments" should {
    "return active enrolments when available" in new Setup {
      //given
      val expectedActiveEnrolmentsSet = Set("some-key")

      //when
      val returnedActiveEnrolments = await(ruleContext.activeEnrolmentKeys)

      //then
      expectedActiveEnrolmentsSet shouldBe returnedActiveEnrolments

      //and
      verify(mockFrontendAuthConnector).currentCoAFEAuthority()
      verify(mockFrontendAuthConnector).getEnrolments(enrolmentsUri)
      verifyNoMoreInteractions(allMocks: _*)
    }
  }

  "notActivatedEnrolments" should {
    "return not activated enrolments when available" in new Setup {
      //given
      val expectedActiveEnrolmentsSet = Set("some-other-key")

      //when
      val returnedActiveEnrolments = await(ruleContext.notActivatedEnrolmentKeys)

      //then
      expectedActiveEnrolmentsSet shouldBe returnedActiveEnrolments

      //and
      verify(mockFrontendAuthConnector).currentCoAFEAuthority()
      verify(mockFrontendAuthConnector).getEnrolments(enrolmentsUri)
      verifyNoMoreInteractions(allMocks: _*)
    }
  }

  "saUserInfo" should {
    "return the last self-assessment return if available in SA and the user has an SA account" in {
      //given
      val saReturn = SaReturn(List("something"))

      implicit val hc = HeaderCarrier.fromHeadersAndSession(FakeRequest().headers)

      val mockSelfAssessmentConnector = mock[SelfAssessmentConnector]
      val expectedUtr = "123456789"
      when(mockSelfAssessmentConnector.lastReturn(expectedUtr)(hc)).thenReturn(Future(saReturn))

      val authContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts(sa = Some(SaAccount("", SaUtr(expectedUtr))))), None)
      //and
      val ruleContext = new RuleContext(authContext) {
        override val selfAssessmentConnector = mockSelfAssessmentConnector
      }

      //then
      await(ruleContext.lastSaReturn) shouldBe saReturn
      verify(mockSelfAssessmentConnector).lastReturn(expectedUtr)
    }

    "return an empty self-assessment return if the user has no SA account" in {
      //given
      val mockSelfAssessmentConnector = mock[SelfAssessmentConnector]

      implicit val hc = HeaderCarrier.fromHeadersAndSession(FakeRequest().headers)

      val authContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts()), None)
      //and
      val ruleContext = new RuleContext(authContext) {
        override val selfAssessmentConnector = mockSelfAssessmentConnector
      }
      //then
      await(ruleContext.lastSaReturn) shouldBe SaReturn.empty

      verifyZeroInteractions(mockSelfAssessmentConnector)
    }
  }

  "currentCoAFEAuthority" should {
    "return the current authority" in new Setup {
      val result = ruleContext.currentCoAFEAuthority

      await(result) shouldBe expectedCoafeAuthority
      verify(mockFrontendAuthConnector).currentCoAFEAuthority()
      verifyNoMoreInteractions(allMocks: _*)
    }
  }

  "affinityGroup" should {
    "return the affinity group from gg profile" in new Setup {

      val result = await(ruleContext.affinityGroup)

      result shouldBe expectedAffinityGroup

      verify(mockFrontendAuthConnector).currentCoAFEAuthority()
      verify(mockUserDetailsConnector).getUserDetails(userDetailsLink)
      verifyNoMoreInteractions(allMocks: _*)
    }
  }
}
