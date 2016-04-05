package support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import connector.AffinityGroupValue
import play.api.libs.json.Json

trait CommonStubs {

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

  def stubProfileWithInactiveEnrolmentsAndIndividualAffinityGroup() = {
    stubFor(get(urlMatching("/profile"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
            |{
            |"affinityGroup":"Individual",
            |"enrolments":[
            |                {"key": "enr1", "identifier": "5597800686", "state": "NotYetActivated"},
            |                {"key": "enr10", "identifier": "5597800686", "state": "NotYetActivated"}
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

  def stubProfileWithNoEnrolments(affinityGroup: String = AffinityGroupValue.ORGANISATION) = {
    stubFor(get(urlMatching("/profile"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
            |{
            |"affinityGroup":"$affinityGroup",
            |"enrolments":[]
            |}
            | """.stripMargin)))
  }

  def stubProfileToReturnAfter2Seconds() = {
    stubFor(get(urlMatching("/profile"))
      .willReturn(aResponse()
        .withStatus(200)
        .withFixedDelay(2000) // For the tests to correctly fail this value must be greater than the ws.timeout.request used by the test.
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

  def stubProfileToReturn500() = {
    stubFor(get(urlMatching("/profile"))
      .willReturn(aResponse()
        .withStatus(500)))
  }

  def stubSaReturnWithNoPreviousReturns(saUtr: String) = {
    stubFor(get(urlMatching(s"/sa/individual/$saUtr/last-return"))
      .willReturn(aResponse()
        .withStatus(404)))
  }

  def stubSaReturnToReturn500(saUtr: String) = {
    stubFor(get(urlMatching(s"/sa/individual/$saUtr/last-return"))
      .willReturn(aResponse()
        .withStatus(500)
        .withBody(
          s"""
             |{
             | "status": 500,
             | "error": "some error"
             |}
             | """.stripMargin)))
  }

  def stubSaReturnToProperlyRespondAfter2Seconds(saUtr: String) = {
    stubFor(get(urlMatching(s"/sa/individual/$saUtr/return/last"))
      .willReturn(aResponse()
        .withStatus(200)
        .withFixedDelay(2000) // For the tests to correctly fail this value must be greater than the ws.timeout.request used by the test.
        .withBody(
          s"""
             |{
             | "previousReturns":true,
             | "supplementarySchedules":${Json.toJson(List.empty[String])}
             |}
             | """.stripMargin)))
  }

  def stubSaReturn(saUtr: String, previousReturns: Boolean = false, supplementarySchedules: List[String] = List.empty) = {
    stubFor(get(urlMatching(s"/sa/individual/$saUtr/return/last"))
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

  def stubAuditEvent() = postRequestedFor(urlMatching("/write/audit.*"))

  def stubProfileWithMoreThanOneSAEnrolment() = {
    stubFor(get(urlMatching("/profile"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
            |{
            |"affinityGroup":"Organisation",
            |"enrolments":[
            |                {"key": "enr1", "identifier": "5597800686", "state": "Activated"},
            |                {"key": "enr4", "identifier": "5597800686", "state": "Activated"}
            |             ]
            |}
            | """.stripMargin)))
  }
}
