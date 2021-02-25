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

package model

import connector._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import support.UnitSpec
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class RuleContextSpec extends UnitSpec with MockitoSugar {

  sealed trait Setup {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockSelfAssessmentConnector: SelfAssessmentConnector = mock[SelfAssessmentConnector]
    val ruleContext = new RuleContext(mockAuthConnector, mockSelfAssessmentConnector)

    val expectedEnrolmentsSeq: Enrolments =
      Enrolments(
        Set(Enrolment("some-key", Seq(EnrolmentIdentifier("key-1", "value-1")), EnrolmentState.ACTIVATED),
          Enrolment("some-other-key", Seq(EnrolmentIdentifier("key-2", "value-2")), EnrolmentState.NOT_YET_ACTIVATED)
        )
      )
  }

  "activeEnrolments" should {
    "return active enrolments when available" in new Setup {
      val expectedActiveEnrolmentsSet = Set("some-key")

      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(Future.successful(expectedEnrolmentsSeq))
      val returnedActiveEnrolments: Set[String] = await(ruleContext.activeEnrolmentKeys)

      expectedActiveEnrolmentsSet shouldBe returnedActiveEnrolments
    }
  }

  "notActivatedEnrolments" should {
    "return not activated enrolments when available" in new Setup {
      val expectedActiveEnrolmentsSet = Set("some-other-key")

      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(Future.successful(expectedEnrolmentsSeq))
      val returnedActiveEnrolments: Set[String] = await(ruleContext.notActivatedEnrolmentKeys)

      expectedActiveEnrolmentsSet shouldBe returnedActiveEnrolments
    }
  }

  "saUserInfo" should {
    "return the last self-assessment return if available in SA and the user has an SA account" in new Setup {
      val saReturn: SaReturn = SaReturn(List("something"))
      val expectedUtr = "123456789"

      when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.successful(Some(expectedUtr)))
      when(mockSelfAssessmentConnector.lastReturn(expectedUtr)(hc)).thenReturn(Future(saReturn))

      await(ruleContext.lastSaReturn) shouldBe saReturn
      verify(mockSelfAssessmentConnector).lastReturn(expectedUtr)
    }

    "return an empty self-assessment return if the user has no SA account" in new Setup {
      when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.successful(None))

      await(ruleContext.lastSaReturn) shouldBe SaReturn.empty
      verifyNoMoreInteractions(mockSelfAssessmentConnector)
    }
  }

  "affinityGroup" should {
    "return the affinity group from gg profile" in new Setup {
      val expectedAffinityGroup: AffinityGroup.Organisation.type = Organisation

      when(mockAuthConnector.authorise[Option[AffinityGroup]](any(), any())(any(), any())).thenReturn(Future.successful(Some(expectedAffinityGroup)))

      val result: String = await(ruleContext.affinityGroup)
      result shouldBe expectedAffinityGroup.toString
    }
  }

}
