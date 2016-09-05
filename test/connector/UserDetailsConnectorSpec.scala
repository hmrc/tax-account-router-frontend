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

package connector

import connector.CredentialRole.{Unknown, User}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost, HttpReads}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class UserDetailsConnectorSpec extends UnitSpec with MockitoSugar {

  sealed trait Setup {

    val mockHttp = mock[WSHttp]

    val someServiceUrl = "/some-service-url"

    val hc = HeaderCarrier()

    val connectorUnderTest = new UserDetailsConnector {

      override def http: HttpGet with HttpPost = mockHttp

      override val serviceUrl = someServiceUrl

    }
  }

  "getUserDetails" should {
    "get user details from user-details microservice" in new Setup {

      val userDetailsUri = "userDetailsUri"

      val expected = UserDetails(User, affinityGroup = "Individual")
      when(mockHttp.GET(eqTo(userDetailsUri))(any[HttpReads[UserDetails]], eqTo(hc))).thenReturn(Future.successful(expected))

      val result = await(connectorUnderTest.getUserDetails(userDetailsUri)(hc))

      result shouldBe expected

      verify(mockHttp).GET[UserDetails](eqTo(userDetailsUri))(any[HttpReads[UserDetails]], eqTo(hc))
      verifyNoMoreInteractions(mockHttp)
    }
  }

}

class UserDetailsDeserializationSpec extends UnitSpec {
  "reads of user details" should {
    "read credentialRole if available" in {
      Json.parse("""{"credentialRole":"User","affinityGroup":"Organisation"}""").as[UserDetails] shouldBe UserDetails(User, "Organisation")
    }

    "read any other role as unknown" in {
      Json.parse("""{"credentialRole":"FooBar","affinityGroup":"Baz"}""").as[UserDetails] shouldBe UserDetails(Unknown, "Baz")
    }
  }
}
