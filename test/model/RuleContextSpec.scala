/*
 * Copyright 2015 HM Revenue & Customs
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
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class RuleContextSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  "Rule Context" should {
    "return active enrolments" in {
      //given
      val userId = "userId"
      val profileResponse = new ProfileResponse(
        affinityGroup = "",
        enrolments = List(Enrolment("enr1", "identifier1", EnrolmentState.ACTIVATED), Enrolment("enr2", "identifier2", EnrolmentState.NOT_YET_ACTIVATED))
      )
      val mockGovernmentGatewayConnector = mock[GovernmentGatewayConnector]
      when(mockGovernmentGatewayConnector.profile(userId)).thenReturn(profileResponse)

      //and
      implicit lazy val request = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

      //and
      val expectedActiveEnrolments: Set[String] = Set("enr1")

      val ruleContext = new RuleContext(userId) {
        override val governmentGatewayConnector = mockGovernmentGatewayConnector
      }

      //when
      val returnedActiveEnrolments: Set[String] = await(ruleContext.activeEnrolments)

      //then
      expectedActiveEnrolments shouldBe returnedActiveEnrolments

      //and
      verify(mockGovernmentGatewayConnector).profile(eqTo(userId))(eqTo(hc))
    }

    "return SA user info" in {
      //given
      val userId = "userId"
      val expectedSaUserInfo = mock[SAUserInfo]

      val mockSelfAssessmentGatewayConnector = mock[SelfAssessmentGatewayConnector]
      when(mockSelfAssessmentGatewayConnector.getInfo(eqTo(userId))(any[ExecutionContext])).thenReturn(Future(expectedSaUserInfo))

      //and
      implicit lazy val request = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

      //and
      val ruleContext = new RuleContext(userId) {
        override val selfAssessmentGatewayConnector = mockSelfAssessmentGatewayConnector
      }

      //when
      val returnedSAUserInfo: SAUserInfo = await(ruleContext.saUserInfo)

      //then
      returnedSAUserInfo shouldBe expectedSaUserInfo

      //and
      verify(mockSelfAssessmentGatewayConnector).getInfo(eqTo(userId))(any[ExecutionContext])
    }

  }


}
