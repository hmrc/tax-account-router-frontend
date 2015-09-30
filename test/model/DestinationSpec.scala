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
import org.scalatest.prop.Tables.Table
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor3}
import play.api.mvc.{AnyContent, Request, Session}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class DestinationSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  "BTA" should {
    "always return BTA location" in {
      implicit lazy val user: AuthContext = mock[AuthContext]
      implicit lazy val hc: HeaderCarrier = mock[HeaderCarrier]
      implicit lazy val request: Request[AnyContent] = mock[Request[AnyContent]]
      val location: Future[Option[Location]] = BTA.getLocation

      await(location) shouldBe Some(BTA.location)
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

      TableDrivenPropertyChecks.forAll(scenarios) { (scenario: String, sessionData: Map[String, String], expectedLocation: Option[Location]) =>
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
