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

import connector.{Enrolment, EnrolmentState, GovernmentGatewayConnector, ProfileResponse}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor3
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.connectors.domain.LevelOfAssurance
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class DestinationSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  "BTA" should {
    "return BTA location if the user has any business enrolments" in {

      val mockGovernmentGatewayConnector = mock[GovernmentGatewayConnector]

      object TestBTA extends BTADestination {
        override val governmentGatewayConnector: GovernmentGatewayConnector = mockGovernmentGatewayConnector
      }

      val scenarios =
        Table(
          ("scenario", "expectedEnrolments", "expectedLocation", "tokenPresent"),
          ("Account with matching activated business enrolments and token is present", List(Enrolment("enr1", "", EnrolmentState.ACTIVATED)), Some(BTA.location), true),
          ("Account with matching activated business enrolments and without token", List(Enrolment("enr1", "", EnrolmentState.ACTIVATED)), None, false),
          ("Account with matching not yet activated business enrolments", List(Enrolment("enr1", "", EnrolmentState.NOT_YET_ACTIVATED)), None, true),
          ("Account without business enrolments", List(), None, true)
        )

      forAll(scenarios) { (scenario: String, expectedEnrolments: List[Enrolment], expectedLocation: Option[Location], tokenPresent: Boolean) =>
        val userId: String = "userId"
        val loggedInUser = LoggedInUser(userId, None, None, None, LevelOfAssurance.LOA_1)

        implicit val user: AuthContext = AuthContext(loggedInUser, mock[Principal], None)
        implicit lazy val request = tokenPresent match {
          case false => FakeRequest()
          case true => FakeRequest().withSession(("token", "token"))
        }
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

        val profileResponse = ProfileResponse("", expectedEnrolments)
        when(mockGovernmentGatewayConnector.profile(userId)).thenReturn(Future(profileResponse))

        val location: Future[Option[Location]] = TestBTA.getLocation

        await(location) shouldBe expectedLocation

        if (tokenPresent)
          verify(mockGovernmentGatewayConnector).profile(Matchers.eq(userId))(Matchers.eq(hc))
      }
    }
  }

  "PTA" should {
    "return PTA location only when 'token' header is present in the request" in {
      val scenarios: TableFor3[String, Map[String, String], Option[Location]] =
        Table(
          ("scenario", "sessionData", "expectedLocation"),
          ("token header is present", Map("token" -> "some-value"), None),
          ("token header is absent", Map(), Some(PTA.location))
        )

      forAll(scenarios) { (scenario: String, sessionData: Map[String, String], expectedLocation: Option[Location]) =>
        implicit lazy val user: AuthContext = mock[AuthContext]
        implicit lazy val request: Request[AnyContent] = FakeRequest().withSession(sessionData.toSeq: _*)
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

        val location: Future[Option[Location]] = PTA.getLocation

        await(location) shouldBe expectedLocation
      }
    }
  }
}
