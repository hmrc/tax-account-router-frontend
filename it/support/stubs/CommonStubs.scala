package support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json

trait CommonStubs {

  def stubUserDetails(affinityGroup: String) = {
    stubFor(get(urlMatching("/user-details-uri"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""
               |{
               |    "name": "test",
               |    "email": "test@test.com",
               |    "affinityGroup": "$affinityGroup",
               |    "description": "description",
               |    "lastName": "test",
               |    "dateOfBirth": "1980-06-31",
               |    "postcode": "NW94HD",
               |    "authProviderId": "12345-credId",
               |    "authProviderType": "Verify"
               |}
      """.stripMargin)
      ))
  }

  def stubUserDetailsToReturn500() = {
    stubFor(get(urlMatching("/user-details-uri"))
      .willReturn(
        aResponse()
          .withStatus(500)
      ))
  }

  def stubBusinessEnrolments() = {
    stubFor(get(urlEqualTo("/enrolments-uri"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            """
              |[
              |   {"key": "enr1", "identifiers": [{"key": "k1", "value": "5597800686"}], "state": "Activated"}
              |]
            """.stripMargin)
      ))
  }

  def stubInactiveEnrolments() = {
    stubFor(get(urlEqualTo("/enrolments-uri"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            """
              |[
              |   {"key": "enr1", "identifiers": [{"key": "k1", "value": "5597800686"}], "state": "NotYetActivated"},
              |   {"key": "enr10", "identifiers": [{"key": "k2", "value": "5597800687"}], "state": "NotYetActivated"}
              |]
            """.stripMargin)
      ))
  }

  def stubSelfAssessmentEnrolments() = {
    stubFor(get(urlEqualTo("/enrolments-uri"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            """
              |[
              |   {"key": "enr3", "identifiers": [{"key": "k1", "value": "5597800686"}], "state": "Activated"}
              |]
            """.stripMargin)
      ))
  }

  def stubNoEnrolments() = {
    stubFor(get(urlMatching("/enrolments-uri"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody("[]")))
  }

  def stubEnrolmentsToReturnAfter2Seconds() = {
    stubFor(get(urlEqualTo("/enrolments-uri"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withFixedDelay(2000) // For the tests to correctly fail this value must be greater than the ws.timeout.request used by the test.
          .withBody(
            """
              |[
              |   {"key": "enr3", "identifiers": [{"key": "k1", "value": "5597800686"}], "state": "Activated"}
              |]
            """.stripMargin)
      ))
  }

  def stubEnrolmentsToReturn500() = {
    stubFor(get(urlMatching("/enrolments-uri"))
      .willReturn(aResponse()
        .withStatus(500)))
  }

  def stubSaReturnWithNoPreviousReturns(saUtr: String) = {
    stubFor(get(urlMatching(s"/sa/individual/$saUtr/last-return"))
      .willReturn(aResponse()
        .withStatus(404)))
  }

  def stubAuthToReturn401() = stubFor(get(urlEqualTo("/auth/authority"))
    .willReturn(
      aResponse()
        .withStatus(401)))

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

  def stubMoreThanOneSAEnrolment() = {
    stubFor(get(urlEqualTo("/enrolments-uri"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            """
              |[
              |   {"key": "enr1", "identifiers": [{"key": "k1", "value": "5597800686"}], "state": "Activated"},
              |   {"key": "enr4", "identifiers": [{"key": "k2", "value": "5597800687"}], "state": "Activated"}
              |]
            """.stripMargin)
      ))
  }
}
