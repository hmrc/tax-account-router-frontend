package support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._

trait CommonStubs {
  def stubSave4LaterWelcomePageSeen() = {
    stubFor(get(urlMatching("/save4later/business-tax-account/.*"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
            |{
            |    "id": "some-session-id",
            |    "data": {
            |        "welcomePageSeen": true
            |    }
            |}
            | """.stripMargin)))
  }
}
