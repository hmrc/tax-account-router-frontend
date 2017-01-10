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

import ch.qos.logback.classic.Level
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.LoneElement
import org.scalatest.mock.MockitoSugar
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class SelfAssessmentConnectorSpec extends UnitSpec with WithFakeApplication with MockitoSugar with LogCapturing with LoneElement {

  "lastReturn" should {
    "return a saReturn object when the return is found" in new Setup {
      val expectedSaReturn = SaReturn(List("individual_tax_form", "self_employment"), previousReturns = true)
      when(httpMock.GET[SaReturn](eqTo(s"/sa/individual/$utr/return/last"))(any(), any[HeaderCarrier])).thenReturn(Future.successful(expectedSaReturn))

      val saReturn = await(connectorUnderTest.lastReturn(utr))
      saReturn shouldBe expectedSaReturn

      verify(httpMock).GET[SaReturn](eqTo(s"/sa/individual/$utr/return/last"))(any(), any[HeaderCarrier])
    }

    "return an empty saReturn (previousReturn is false) when the return is not found" in new Setup {
      val expectedSaReturn = SaReturn(List.empty, previousReturns = false)
      when(httpMock.GET[SaReturn](eqTo(s"/sa/individual/$utr/return/last"))(any(), any[HeaderCarrier])).thenReturn(Future.successful(expectedSaReturn))

      val saReturn = await(connectorUnderTest.lastReturn(utr))
      saReturn shouldBe SaReturn(List.empty, previousReturns = false)

      verify(httpMock).GET[SaReturn](eqTo(s"/sa/individual/$utr/return/last"))(any(), any[HeaderCarrier])
    }

    "log warning and re-throw exception when SA is returning status code different from 2xx or 404" in new Setup {
      val expectedException = new Upstream5xxResponse("error", 500, 500)
      when(httpMock.GET[SaReturn](eqTo(s"/sa/individual/$utr/return/last"))(any(), any[HeaderCarrier])).thenReturn(Future.failed(expectedException))

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
      val expectedObject = SaReturn(List("individual_tax_form", "self_employment"), previousReturns = true)
      val currentObject = SaReturn.reads.reads(json)
      currentObject.get shouldBe expectedObject
    }
  }

  trait Setup {
    implicit val hc = HeaderCarrier()
    val httpMock = mock[HttpGet]

    val connectorUnderTest = new SelfAssessmentConnector {
      override val http = httpMock
      override val serviceUrl: String = ""
    }
    val utr = "some-utr"
  }

}
