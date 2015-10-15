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

package connector

import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.{HttpGet, HttpResponse}
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}

import scala.concurrent.Future

class SelfAssessmentConnectorSpec extends UnitSpec with WithFakeApplication {

  val response = """{"utr":"5328981911","supplementarySchedules":["individual_tax_form","self_employment"]}"""

  implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(FakeRequest().headers)

  val connectorUnderTest = new SelfAssessmentConnector {
    override def http =  new HttpGet {

      override protected def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
        url match {
          case "/sa/individual/5328981911/last-return" => Future.successful(HttpResponse(200, Some(Json.parse(response))))
          case _ => Future.successful(HttpResponse(404))
        }
      }
        Future.successful(HttpResponse(200, Some(Json.parse(response))))

      override def appName: String = ???
      override def auditConnector: AuditConnector = ???
    }

    override val serviceUrl: String = ""
  }


  "Calls to the self assessment microservice" should {
    "be mapped to a saReturn object when the return is found" in {
      val saReturn: SaReturn = await(connectorUnderTest.lastReturn("5328981911"))
      saReturn shouldBe SaReturn(List("individual_tax_form","self_employment"), previousReturns = true)
    }
    "be mapped to an empty saReturn (previousReturn is false) when the return is not found" in {
      val saReturn: SaReturn = await(connectorUnderTest.lastReturn("other-utr"))
      saReturn shouldBe SaReturn(List.empty, previousReturns = false)
    }
  }



}
