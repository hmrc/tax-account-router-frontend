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
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, SaAccount}
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RuleContextSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  "activeEnrolments" should {
    "return active enrolments when available" in {
      //given
      val profileResponse = ProfileResponse(
        affinityGroup = "",
        enrolments = List(Enrolment("enr1", "identifier1", EnrolmentState.ACTIVATED), Enrolment("enr2", "identifier2", EnrolmentState.NOT_YET_ACTIVATED))
      )
      val mockGovernmentGatewayConnector = mock[GovernmentGatewayConnector]
      when(mockGovernmentGatewayConnector.profile).thenReturn(profileResponse)

      //and
      implicit lazy val request = FakeRequest()
      implicit lazy val hc = HeaderCarrier.fromHeadersAndSession(request.headers)

      //and
      val expectedActiveEnrolments: Set[String] = Set("enr1")

      val ruleContext = new RuleContext(mock[AuthContext]) {
        override val governmentGatewayConnector = mockGovernmentGatewayConnector
      }

      //when
      val returnedActiveEnrolments: Set[String] = await(ruleContext.activeEnrolments)

      //then
      expectedActiveEnrolments shouldBe returnedActiveEnrolments

      //and
      verify(mockGovernmentGatewayConnector).profile(eqTo(hc))
    }
  }

  "notActivatedEnrolments" should {
    "return not activated enrolments when available" in {
      //given
      val profileResponse = ProfileResponse(
        affinityGroup = "",
        enrolments = List(
          Enrolment("enr1", "identifier1", EnrolmentState.ACTIVATED),
          Enrolment("enr2", "identifier2", EnrolmentState.NOT_YET_ACTIVATED),
          Enrolment("enr3", "identifier2", EnrolmentState.HANDED_TO_AGENT),
          Enrolment("enr4", "identifier2", EnrolmentState.PENDING)
        )
      )
      val mockGovernmentGatewayConnector = mock[GovernmentGatewayConnector]
      when(mockGovernmentGatewayConnector.profile).thenReturn(profileResponse)

      //and
      implicit lazy val request = FakeRequest()
      implicit lazy val hc = HeaderCarrier.fromHeadersAndSession(request.headers)

      //and
      val expectedInactiveEnrolments = Set("enr2", "enr3", "enr4")

      val ruleContext = new RuleContext(mock[AuthContext]) {
        override val governmentGatewayConnector = mockGovernmentGatewayConnector
      }

      //when
      val result = await(ruleContext.notActivatedEnrolments)

      //then
      result shouldBe expectedInactiveEnrolments

      //and
      verify(mockGovernmentGatewayConnector).profile(eqTo(hc))
      verifyNoMoreInteractions(mockGovernmentGatewayConnector)
    }
  }

  "saUserInfo" should {
    "return the last self-assessment return if available in SA and the user has an SA account" in {
      //given

      val saReturn = SaReturn(List("something"))

      implicit val hc = HeaderCarrier.fromHeadersAndSession(FakeRequest().headers)

      val mockSelfAssessmentConnector = mock[SelfAssessmentConnector]
      when(mockSelfAssessmentConnector.lastReturn("123456789")(hc)).thenReturn(Future(saReturn))

      val authContext = AuthContext(mock[LoggedInUser], Principal(None, Accounts(sa = Some(SaAccount("", SaUtr("123456789"))))), None)
      //and
      val ruleContext = new RuleContext(authContext) {
        override val selfAssessmentConnector = mockSelfAssessmentConnector
      }

      //then
      await(ruleContext.lastSaReturn) shouldBe saReturn
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
    "return the current authority" in {
      implicit val hc = HeaderCarrier.fromHeadersAndSession(FakeRequest().headers)

      val mockFrontendAuthConnector = mock[FrontendAuthConnector]
      val currentAuthority = CoAFEAuthority(Some("1234"))
      when(mockFrontendAuthConnector.currentCoAFEAuthority()(hc)).thenReturn(Future(currentAuthority))

      val authContext = mock[AuthContext]

      val ruleContext = new RuleContext(authContext) {
        override val frontendAuthConnector = mockFrontendAuthConnector
      }

      val result = ruleContext.currentCoAFEAuthority

      await(result) shouldBe currentAuthority
    }
  }

  "affinityGroup" should {
    "return the affinity group from gg profile" in {
      implicit val hc = HeaderCarrier.fromHeadersAndSession(FakeRequest().headers)

      val expectedAffinityGroup = "some-affinity-group"

      val profileResponse = ProfileResponse(
        affinityGroup = expectedAffinityGroup,
        enrolments = List.empty
      )
      val mockGovernmentGatewayConnector = mock[GovernmentGatewayConnector]
      when(mockGovernmentGatewayConnector.profile).thenReturn(profileResponse)

      val authContext = mock[AuthContext]

      val ruleContext = new RuleContext(authContext) {
        override val governmentGatewayConnector = mockGovernmentGatewayConnector
      }

      val result = ruleContext.affinityGroup

      await(result) shouldBe expectedAffinityGroup

      verify(mockGovernmentGatewayConnector).profile(any[HeaderCarrier])
      verifyNoMoreInteractions(mockGovernmentGatewayConnector)
    }
  }
}
