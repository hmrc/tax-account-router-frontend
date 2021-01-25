package support.stubs

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping

trait StubHelper {

  def stubGet(url: String, status: Int, body: Option[String]): StubMapping = {
    stubFor(
      get(urlEqualTo(url)) willReturn {
        val response: ResponseDefinitionBuilder = aResponse().withStatus(status)
        body match {
          case Some(x) => response.withBody(x)
          case _ => response
        }
      }
    )
  }

  def stubPost(url: String, status: Int, postBody: String, responseBody: Option[String]): StubMapping = {
    stubFor(
      post(urlEqualTo(url))
        .withRequestBody(equalToJson(postBody)) willReturn {
        val response: ResponseDefinitionBuilder = aResponse().withStatus(status)
        responseBody match {
          case Some(x) => response.withBody(x)
          case _ => response
        }
      }
    )
  }

  def stubPostEmpty(url: String, status: Int): StubMapping = {
    stubFor(
      post(urlMatching(url)) willReturn {
        aResponse().withStatus(status)
      }
    )
  }

  def stubGetWithQuery(url: String, status: Int, queryName: String, queryValue: String, body: Option[String]): StubMapping = {
    stubFor(
      get(urlPathEqualTo(url)).withQueryParam(queryName, equalTo(queryValue)) willReturn {
        val response: ResponseDefinitionBuilder = aResponse().withStatus(status)
        body match {
          case Some(x) => response.withBody(x)
          case _ => response
        }
      }
    )
  }
}
