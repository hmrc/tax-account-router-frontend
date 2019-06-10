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

package connector

import config.HttpClient
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class UserDetailsConnectorSpec extends UnitSpec with MockitoSugar {

  sealed trait Setup {

    val mockHttp = mock[HttpClient]

    val someServiceUrl = "/some-service-url"

    implicit val hc: HeaderCarrier = HeaderCarrier()

    implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

    val connectorUnderTest = new UserDetailsConnector {

      override def httpClient: HttpClient = mockHttp

      override val serviceUrl = someServiceUrl

    }
  }

  "getUserDetails" should {
    "get user details from user-details microservice" in new Setup {

      val userDetailsUri = "userDetailsUri"

      val expected = UserDetails(Some(CredentialRole("User")), affinityGroup = "Individual")
      when(mockHttp.GET(eqTo(userDetailsUri))(any[HttpReads[UserDetails]](), eqTo(hc),eqTo(ec))).thenReturn(Future.successful(expected))

      val result = await(connectorUnderTest.getUserDetails(userDetailsUri))

      result shouldBe expected

      verify(mockHttp).GET[UserDetails](eqTo(userDetailsUri))(any[HttpReads[UserDetails]](), eqTo(hc), eqTo(ec))
      verifyNoMoreInteractions(mockHttp)
    }
  }

}

class UserDetailsDeserializationSpec extends UnitSpec {
  "reads of user details" should {
    "read credentialRole if available" in {
      Json.parse("""{"credentialRole":"User","affinityGroup":"Organisation"}""").as[UserDetails] shouldBe UserDetails(Some(CredentialRole("User")), "Organisation")
    }

    "read credentialRole as None if not available" in {
      Json.parse("""{"affinityGroup":"Baz"}""").as[UserDetails] shouldBe UserDetails(None, "Baz")
    }
  }
}

class CredentialRoleSpec extends UnitSpec {
  "isAdmin" should {
    val expectedAffinityGroup = "affinityGroup"
    val scenarios = Table(
      ("role", "result"),
      (CredentialRole("User"), true),
      (CredentialRole("Assistant"), false)
    )

    forAll(scenarios) {
      (role: CredentialRole, expectedResult: Boolean) =>
        s"return $expectedResult if user has credential role $role" in {
          role.isAdmin shouldBe expectedResult
        }
    }
  }
}
