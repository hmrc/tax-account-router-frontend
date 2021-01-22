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

import ch.qos.logback.classic.Level
import config.FrontendAppConfig
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.LoneElement
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Logger
import play.api.libs.json.Json
import support._
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class SelfAssessmentConnectorSpec extends UnitSpec with MockitoSugar with LogCapturing with LoneElement with GuiceOneAppPerSuite {

  "lastReturn" should {
    "return a saReturn object when the return is found" in new Setup {
      val expectedSaReturn: SaReturn = SaReturn(List("individual_tax_form", "self_employment"))
      when(configurationMock.saServiceUrl).thenReturn(saServiceUrl)
      when(
        httpMock.GET[SaReturn](eqTo(s"$saServiceUrl/sa/individual/$utr/return/last"))(any(), any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(expectedSaReturn))

      val saReturn: SaReturn = await(connectorUnderTest.lastReturn(utr))
      saReturn shouldBe expectedSaReturn

      verify(httpMock).GET[SaReturn](eqTo(s"$saServiceUrl/sa/individual/$utr/return/last"))(any(), any[HeaderCarrier], any[ExecutionContext])
    }

    "return an empty saReturn (previousReturn is false) when the return is not found" in new Setup {
      val expectedSaReturn: SaReturn = SaReturn(List.empty, previousReturns = false)
      when(configurationMock.saServiceUrl).thenReturn(saServiceUrl)
      when(httpMock.GET[SaReturn](eqTo(s"$saServiceUrl/sa/individual/$utr/return/last"))(any(), any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(expectedSaReturn))

      val saReturn: SaReturn = await(connectorUnderTest.lastReturn(utr))
      saReturn shouldBe SaReturn(List.empty, previousReturns = false)

      verify(httpMock).GET[SaReturn](eqTo(s"$saServiceUrl/sa/individual/$utr/return/last"))(any(), any[HeaderCarrier], any[ExecutionContext])
    }

    "log warning and re-throw exception when SA is returning status code different from 2xx or 404" in new Setup {
      val expectedException: Upstream5xxResponse = Upstream5xxResponse("error", 500, 500)
      when(configurationMock.saServiceUrl).thenReturn(saServiceUrl)
      when(httpMock.GET[SaReturn](eqTo(s"$saServiceUrl/sa/individual/$utr/return/last"))(any(), any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(expectedException))

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        intercept[Upstream5xxResponse] {
          await(connectorUnderTest.lastReturn(utr))
        }

        val logElement = logEvents.loneElement
        logElement.getMessage shouldBe s"Unable to retrieve last sa return details for user with utr $utr"
        logElement.getLevel shouldBe Level.WARN
      }
    }

    "deserialize a SA json into a SaReturn object" in {
      val json = Json.parse("""{"utr":"5328981911", "supplementarySchedules":["individual_tax_form","self_employment"]}""")
      val expectedObject = SaReturn(List("individual_tax_form", "self_employment"))
      val currentObject = SaReturn.reads.reads(json)
      currentObject.get shouldBe expectedObject
    }
  }

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
    val httpMock: HttpClient = mock[HttpClient]
    val configurationMock: FrontendAppConfig = mock[FrontendAppConfig]
    val connectorUnderTest = new SelfAssessmentConnector(httpMock, configurationMock)
    val utr = "some-utr"
    val saServiceUrl = "saServiceUrl"
  }

}
