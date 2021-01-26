package support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import connector.AnalyticsData
import play.api.libs.json.Json


trait CommonStubs {

  val saUtr: String = "12345"

  def verifyAnalytics(analyticsData: AnalyticsData): Unit = {
    verify(1, postRequestedFor(urlEqualTo("/platform-analytics/event"))
      .withRequestBody(equalToJson(Json.toJson(analyticsData).toString())))
  }

  def stubRetrievalALLEnrolments(enrolmentKey: String = "enr3", utr: String = "12345", hasEnrolments: Boolean = true, responsive: Boolean = true): StubMapping = {
    stubFor(post(urlEqualTo("/auth/authorise")).withRequestBody(equalToJson(""" {"authorise":[],"retrieve":["allEnrolments"]} """.stripMargin))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            if (!responsive) {
              s"".stripMargin
            } else if (hasEnrolments) {
              s"""
                 |{"allEnrolments":[{"key":"$enrolmentKey","identifiers":[{"key":"UTR","value":"$utr"}],"state":"Activated"}]}
            """.stripMargin
            } else {
              s"""
                 |{"allEnrolments":[]}
            """.stripMargin
            }
          )
      ))
  }

  def stubNotAuthenticatedUser(): StubMapping = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .withRequestBody(equalToJson("""{"authorise":[], "retrieve":[]}""".stripMargin))
      .willReturn(
        aResponse()
          .withStatus(401)
          .withHeader("WWW-Authenticate", s"""MDTP detail="MissingBearerToken"""")

      )
    )
  }

  // TODO not sure how this was working before with an empty Json body "{}" ???
  def stubAuthenticatedUser(): StubMapping = {
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .atPriority(1)
        .withRequestBody(equalToJson("""{"authorise":[], "retrieve":[]}"""))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              s"""{
                 |  "allEnrolments": [
                 |   { "key":"enr3", "identifiers": [
                 |      { "key":"UTR", "value": "12345" }
                 |    ], "state":"Activated"}
                 |  ],
                 |  "optionalCredentials": {
                 |    "providerId": "12345-credId",
                 |    "providerType": "GovernmentGateway"
                 |  }
                 |}""".stripMargin
            )
        )
    )
  }

  def stubVerifyUser(verifyUser: Boolean = true): StubMapping = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .withRequestBody(equalToJson(""" {"authorise":[{"authProviders":["Verify"]}],"retrieve":[]} """.stripMargin))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(
            if (verifyUser) {
              s"""{
                 |  "optionalCredentials": {
                 |    "providerId": "12345-credId",
                 |    "providerType": "Verify"
                 |  }}""".stripMargin
            } else ""
          )
      ))
  }

  def stubGGUser(ggUser: Boolean = true): StubMapping = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .withRequestBody(equalToJson(""" {"authorise":[{"authProviders":["GovernmentGateway"]}],"retrieve":[]} """.stripMargin))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(
            if (ggUser) {
              s"""{
                 |  "optionalCredentials": {
                 |    "providerId": "12345-credId",
                 |    "providerType": "GovernmentGateway"
                 |  }}""".stripMargin
            } else ""
          )
      ))
  }

  def setVerifyUser(): StubMapping = {
    stubAuthenticatedUser()
    stubRetrievalALLEnrolments()
    stubVerifyUser()
    stubRetrievalInternalId()
  }

  def setGGUser(): StubMapping = {
    stubAuthenticatedUser()
    stubRetrievalALLEnrolments()
    stubVerifyUser(false)
    stubGGUser()
    stubRetrievalInternalId()
  }

  def stubRetrievalInternalId(internalId: String = "123456789"): StubMapping = {
    stubFor(post(urlEqualTo("/auth/authorise")).withRequestBody(equalToJson(""" {"authorise":[],"retrieve":["internalId"]} """.stripMargin))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""
               |{"internalId":"$internalId"}
            """.stripMargin)
      ))
  }

  def stubRetrievalAffinityGroup(affinityGroup: String = "Organisation", ready: Boolean = true): StubMapping = {
    stubFor(post(urlEqualTo("/auth/authorise")).withRequestBody(equalToJson(""" {"authorise":[],"retrieve":["affinityGroup"]} """.stripMargin))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            if (ready) {
              s"""
                 |{"affinityGroup":"$affinityGroup"}
            """.stripMargin
            } else ""
          )

      ))
  }

  def stubRetrievalSAUTR(responsive: Boolean = true): StubMapping = {
    stubFor(post(urlEqualTo("/auth/authorise")).withRequestBody(equalToJson(""" {"authorise":[],"retrieve":["saUtr"]} """.stripMargin))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            if (responsive) {
              s"""
                 |{"saUtr":"$saUtr"}
            """.stripMargin
            } else ""
          )
      ))
  }

  def stubRetrievalNINO(hasNino: Boolean = true): StubMapping = {
    stubFor(post(urlEqualTo("/auth/authorise")).withRequestBody(equalToJson(""" {"authorise":[],"retrieve":["nino"]} """.stripMargin))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            if (hasNino) {
              s"""
                 |{"nino":"AA000003D"}
            """.stripMargin
            } else ""
          )
      ))
  }

  def stubSaReturnWithNoPreviousReturns(saUtr: String): StubMapping = {
    stubFor(get(urlMatching(s"/sa/individual/$saUtr/return/last"))
      .willReturn(aResponse().withStatus(404)))
  }

  def stubSaReturnToReturn500(saUtr: String): StubMapping = {
    stubFor(get(urlMatching(s"/sa/individual/$saUtr/return/last"))
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

  def stubSaReturnToProperlyRespondAfter2Seconds(saUtr: String): StubMapping = {
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

  def stubSaReturn(saUtr: String, previousReturns: Boolean = false, supplementarySchedules: List[String] = List.empty): StubMapping = {
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

  def auditEventPattern(): RequestPatternBuilder = postRequestedFor(urlMatching("/write/audit.*"))

  def stubBusinessAccount(): StubMapping = stubFor(get(urlMatching("/business-account.*")).willReturn(
    aResponse().withStatus(200)
  ))

  def stubPersonalAccount(): StubMapping = stubFor(get(urlMatching("/personal-account.*")).willReturn(
    aResponse().withStatus(200)
  ))
}
