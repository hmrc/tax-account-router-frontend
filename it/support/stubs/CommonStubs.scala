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

  def stubSaReturnWithPartnership(saUtr: String) = {
    stubFor(get(urlMatching(s"/sa/individual/$saUtr/last-return"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
            |{
            |"previousReturns":true,
            |"supplementarySchedules":["partnership"]
            |}
            | """.stripMargin)))
  }
}
