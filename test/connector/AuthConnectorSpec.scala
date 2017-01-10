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

package connector

import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.CredentialStrength
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AuthConnectorSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  "currentUserAuthority" should {
    "execute call to auth microservice get the authority" in new Setup {
      val authResponse = UserAuthority(None, Some(""), Some(""), None, CredentialStrength.None, None, None)
      when(mockHttp.GET[Any](eqTo(s"$authUrl/auth/authority"))(any[HttpReads[Any]](), any[HeaderCarrier])).thenReturn(Future.successful(authResponse))

      val result = await(connector.currentUserAuthority)

      result shouldBe authResponse

      verify(mockHttp).GET(eqTo(s"$authUrl/auth/authority"))(any[HttpReads[Any]](), any[HeaderCarrier])
    }
  }

  "userAuthority" should {
    "execute call to auth microservice get the authority" in new Setup {
      val credId = "credId"
      val authResponse = UserAuthority(None, Some(""), Some(""), None, CredentialStrength.None, None, None)
      when(mockHttp.GET[Any](eqTo(s"$authUrl/auth/gg/$credId"))(any[HttpReads[Any]](), any[HeaderCarrier])).thenReturn(Future.successful(authResponse))

      val result = await(connector.userAuthority(credId))

      result shouldBe authResponse

      verify(mockHttp).GET(eqTo(s"$authUrl/auth/gg/$credId"))(any[HttpReads[Any]](), any[HeaderCarrier])
    }
  }

  "authority response" should {
    "parse correctly to Authority domain object" in {
      val userDetailsLink = "/user-details/id/5658962a3d00003d002f3ca1"
      val twoFactorOtpId = "/user-details/id/5658962a3d00003d002f3ca1"
      val credentialStrength = CredentialStrength.Strong
      val saUtr = SaUtr("12345")
      val nino = Nino("CS100700A")
      val idsUri = "/auth/ids-uri"
      val enrolmentsUri = "/auth/enrolments-uri"
      val authResponse =
        s"""{
            |    "userDetailsLink": "$userDetailsLink",
            |    "twoFactorAuthOtpId": "$twoFactorOtpId",
            |    "credentialStrength": "${credentialStrength.name.toLowerCase}",
            |    "nino": "${nino.value}",
            |    "saUtr": "${saUtr.value}",
            |    "ids": "$idsUri",
            |    "enrolments": "$enrolmentsUri"
            |    }""".stripMargin

      Json.parse(authResponse).as[UserAuthority] shouldBe UserAuthority(Some(twoFactorOtpId), Some(idsUri), Some(userDetailsLink), Some(enrolmentsUri), credentialStrength, Some(nino), Some(saUtr))
    }
  }

  trait Setup {

    val mockHttp = mock[HttpGet]
    val authUrl = "auth-service-url"
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val connector = new FrontendAuthConnector {

      override def http: HttpGet = mockHttp

      override val serviceUrl = authUrl
    }
  }

}
