package support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json

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

  def stubProfileWithBusinessEnrolments() = {
    stubFor(get(urlMatching("/profile"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
            |{
            |"affinityGroup":"Organisation",
            |"enrolments":[
            |                {"key": "enr1", "identifier": "5597800686", "state": "Activated"}
            |             ]
            |}
            | """.stripMargin)))
  }

  def stubProfileWithSelfAssessmentEnrolments() = {
    stubFor(get(urlMatching("/profile"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
            |{
            |"affinityGroup":"Organisation",
            |"enrolments":[
            |                {"key": "enr3", "identifier": "5597800686", "state": "Activated"}
            |             ]
            |}
            | """.stripMargin)))
  }

  def stubSaReturnWithNoPreviousReturns(saUtr: String) = {
    stubFor(get(urlMatching(s"/sa/individual/$saUtr/last-return"))
      .willReturn(aResponse()
        .withStatus(404)))
  }

  def stubSaReturn(saUtr: String, previousReturns: Boolean = false, supplementarySchedules: List[String] = List.empty) = {
    stubFor(get(urlMatching(s"/sa/individual/$saUtr/last-return"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             | "previousReturns":$previousReturns,
             | "supplementarySchedules":${Json.toJson(supplementarySchedules)}
             |}
             | """.stripMargin)))
  }
}
