package support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import connector.AffinityGroupValue.ORGANISATION
import connector.CredentialRole
import play.api.libs.json.Json

import scala.util.Random

trait CommonStubs {

  val user = CredentialRole("User")
  val assistant = CredentialRole("Assistant")

  def stubUserDetails(affinityGroup: Option[String] = None, credentialRole: CredentialRole = CredentialRole("User")) = {
    stubFor(get(urlMatching("/user-details-uri"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""
               |{
               |    "name": "test",
               |    "email": "test@test.com",
               |    "affinityGroup": "${affinityGroup.getOrElse(ORGANISATION)}",
               |    "description": "description",
               |    "lastName": "test",
               |    "dateOfBirth": "1980-06-31",
               |    "postcode": "NW94HD",
               |    "authProviderId": "12345-credId",
               |    "credentialRole": "${credentialRole.value}",
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
    stubFor(get(urlEqualTo("/auth/enrolments-uri"))
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

  def stubActiveEnrolments(enrolmentKeys: String*) = {
    val enrolmentsAsJson = enrolmentKeys
      .map(key => s"""{"key": "$key", "identifiers": [{"key": "$key-id", "value": "${Random.nextInt(100000)}"}], "state": "Activated"}""")
      .mkString(",")

    stubFor(get(urlEqualTo("/auth/enrolments-uri"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"[$enrolmentsAsJson]")
      ))

    enrolmentsAsJson
  }

  def stubInactiveEnrolments() = {
    stubFor(get(urlEqualTo("/auth/enrolments-uri"))
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
    stubFor(get(urlEqualTo("/auth/enrolments-uri"))
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

  def stubSelfAssessmentAndVatEnrolments() = {
    stubFor(get(urlEqualTo("/auth/enrolments-uri"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            """
              |[
              |   {"key": "enr3", "identifiers": [{"key": "k1", "value": "5597800686"}], "state": "Activated"},
              |   {"key": "enr5", "identifiers": [{"key": "k2", "value": "5597800687"}], "state": "Activated"}
              |]
            """.stripMargin)
      ))
  }

  def stubNoEnrolments() = {
    stubFor(get(urlMatching("/auth/enrolments-uri"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody("[]")))
  }

  def stubEnrolmentsToReturnAfter2Seconds() = {
    stubFor(get(urlEqualTo("/auth/enrolments-uri"))
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
    stubFor(get(urlMatching("/auth/enrolments-uri"))
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

  def stubMoreThanOneSAEnrolment() = {
    stubFor(get(urlEqualTo("/auth/enrolments-uri"))
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
