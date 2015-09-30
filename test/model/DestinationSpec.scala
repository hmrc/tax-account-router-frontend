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

import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor3
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Session}
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, VatAccount}
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class DestinationSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  "BTA" should {
    "return BTA location if the user has any business enrolments" in {
      val accountWithBusinessEnrolments = Accounts(vat = Some(VatAccount("", Vrn(""))))

      val accountWithoutBusinessEnrolments = Accounts()

      val scenarios =
        Table(
          ("scenario", "accounts", "expectedLocation"),
          ("Account with business enrolments", accountWithBusinessEnrolments, Some(BTA.location)),
          ("Account without business enrolments", accountWithoutBusinessEnrolments, None)
        )

      forAll(scenarios) { (scenario: String, accounts: Accounts, expectedLocation: Option[Location]) =>
        val principal: Principal = Principal(None, accounts)
        implicit val user: AuthContext = AuthContext(mock[LoggedInUser], principal, None)
        implicit lazy val hc: HeaderCarrier = mock[HeaderCarrier]
        implicit lazy val request: Request[AnyContent] = mock[Request[AnyContent]]
        val location: Future[Option[Location]] = BTA.getLocation

        await(location) shouldBe expectedLocation

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
        implicit lazy val hc: HeaderCarrier = mock[HeaderCarrier]
        implicit lazy val request: Request[AnyContent] = mock[Request[AnyContent]]

        val mockSession = mock[Session]
        when(mockSession.data).thenReturn(sessionData)
        when(request.session).thenReturn(mockSession)

        val location: Future[Option[Location]] = PTA.getLocation

        await(location) shouldBe expectedLocation
      }
    }
  }
}
