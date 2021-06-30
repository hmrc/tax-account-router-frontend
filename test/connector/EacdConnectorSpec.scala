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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import support.UnitSpec
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EacdConnectorSpec extends UnitSpec with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val mockHttp = mock[HttpClient]
  val mockConfig: FrontendAppConfig = mock[FrontendAppConfig]

  val connector = new EacdConnector(mockHttp, mockConfig, global){
    override lazy val enrolmentProxyBase: String = "test"
  }

  val responseWithEnrolments = {Json.parse(
    """
      |{
      |    "startRecord": 1,
      |    "totalRecords": 2,
      |    "enrolments": [
      |        {
      |           "service": "IR-SA",
      |           "state": "Activated",
      |           "friendlyName": "My First Client's SA Enrolment",
      |           "enrolmentDate": "2018-10-05T14:48:00.000Z",
      |           "failedActivationCount": 1,
      |           "activationDate": "2018-10-13T17:36:00.000Z",
      |           "identifiers": [
      |              {
      |                 "key": "UTR",
      |                 "value": "1234567890"
      |              }
      |           ]
      |        }
      |    ]
      |}
      |""".stripMargin)
  }



  "checkGroupEnrolments" should {
    "return true" when {
      "user has no group ID" in {
        await(connector.checkGroupEnrolments(None)) shouldBe true
      }
      "EACD returns no group enrolments for an ID" in {
        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(200, """{"enrolments":[]}""")))

        when(mockConfig.enrolmentStore)
          .thenReturn("testValue")

        await(connector.checkGroupEnrolments(Some("groupId"))) shouldBe true
      }
      "EACD returns no group enrolments for an ID with expected 204 code" in {
        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(204, """{}""")))

        when(mockConfig.enrolmentStore)
          .thenReturn("testValue")

        await(connector.checkGroupEnrolments(Some("groupId"))) shouldBe true
      }
    }
    "return false" when {
      "EACD returns group enrolments for an ID" in {
        when(mockHttp.GET[HttpResponse](any(), any(), any())(any(), any(), any()))
          .thenReturn(Future.successful(
            HttpResponse(
              200,
              json = responseWithEnrolments,
              headers = Map()
            ))
          )

        when(mockConfig.enrolmentStore)
          .thenReturn("testValue")


        await(connector.checkGroupEnrolments(Some("groupId"))) shouldBe false
      }

    }
  }

}
