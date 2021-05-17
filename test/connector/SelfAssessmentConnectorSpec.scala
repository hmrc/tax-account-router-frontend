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

package connector

import config.FrontendAppConfig
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.LoneElement
import org.scalatest.mockito.MockitoSugar
import play.api.Logger
import play.api.libs.json.Json
import support._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SelfAssessmentConnectorSpec extends UnitSpec with MockitoSugar with LogCapturing with LoneElement {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val httpMock: HttpClient = mock[HttpClient]
  val configurationMock: FrontendAppConfig = mock[FrontendAppConfig]

  val utr = "some-utr"
  val saServiceUrl = "saServiceUrl"

  trait Setup {
    val connectorUnderTest: SelfAssessmentConnector = new SelfAssessmentConnector(httpMock, configurationMock, global){
      override lazy val serviceUrl: String = saServiceUrl
    }
  }

  "lastReturn" should {
    "return a saReturn object when the return is found" in new Setup {
      val expectedSaReturn: SaReturn = SaReturn(List("individual_tax_form", "self_employment"))

      when(httpMock.GET[SaReturn](eqTo(s"$saServiceUrl/sa/individual/$utr/return/last"), any(), any())(any(), any[HeaderCarrier](), any[ExecutionContext]()))
        .thenReturn(Future.successful(expectedSaReturn))

      val saReturn: SaReturn = await(connectorUnderTest.lastReturn(utr))
      saReturn shouldBe expectedSaReturn
    }

    "return an empty saReturn (previousReturn is false) when the return is not found" in new Setup {
      val expectedSaReturn: SaReturn = SaReturn(List.empty, previousReturns = false)

      when(httpMock.GET[SaReturn](any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(expectedSaReturn))

      val saReturn: SaReturn = await(connectorUnderTest.lastReturn(utr))
      saReturn shouldBe SaReturn(List.empty, previousReturns = false)
    }

    "log warning and re-throw exception when SA is returning status code different from 2xx or 404" in new Setup {
      val expectedException: UpstreamErrorResponse = UpstreamErrorResponse("error", 500, 500)

      when(httpMock.GET[SaReturn](any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.failed(expectedException))

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        intercept[UpstreamErrorResponse] {
          await(connectorUnderTest.lastReturn(utr))
        }

        val logElement = logEvents.loneElement
        logElement.getMessage shouldBe s"Unable to retrieve last sa return details for user with utr $utr"
      }
    }

    "deserialize a SA json into a SaReturn object" in {
      val json = Json.parse("""{"utr":"5328981911", "supplementarySchedules":["individual_tax_form","self_employment"]}""")
      val expectedObject = SaReturn(List("individual_tax_form", "self_employment"))
      val currentObject = SaReturn.reads.reads(json)
      currentObject.get shouldBe expectedObject
    }
  }

}
