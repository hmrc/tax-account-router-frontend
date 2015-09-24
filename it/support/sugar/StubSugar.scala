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

package support.sugar

import com.github.tomakehurst.wiremock.client.WireMock._

trait StubSugar {

  def callUrlAndReturnPayload(url: String)(payload: String, headers: Seq[(String, String)] = Nil) = {
    def response = headers.foldLeft(aResponse()
      .withStatus(200)
      .withBody(payload)) { (response, header) =>
      response.withHeader(header._1, header._2)
    }
    stubFor(get(urlEqualTo(url)).willReturn(response))
  }

  def callUrlAndFailWithStatus(url: String, status: Int) = {
    stubFor(get(urlEqualTo(url))
      .willReturn(
        aResponse()
          .withStatus(status)))
  }

}
