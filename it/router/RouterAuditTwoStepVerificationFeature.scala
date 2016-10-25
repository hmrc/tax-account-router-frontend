package router

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{RequestPatternBuilder, WireMock}
import connector.CredentialRole
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeApplication
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => mutableMap}

class RouterAuditTwoStepVerificationFeature extends StubbedFeatureSpec with CommonStubs {

  val rule = Map(
    "enrolments" -> "some-enrolment-category"
  )

  val locations = Map(
    "location-1.name" -> "some-location-1",
    "location-1.url" -> "/some-location-1",
    "location-2.name" -> "some-location-2",
    "location-2.url" -> "/some-location-2"
  )


  val optionalOid = "bbbb"  // 83.2 (percentage calculated from hashcode)
  val mandatoryOid = "aaaa" // 4.8  (percentage calculated from hashcode)
  val throttle = 50 // registration is mandatory if the percentage is smaller than throttle value

  val additionalConfiguration = Map[String, Any](
    "auditing.consumer.baseUri.host" -> stubHost,
    "auditing.consumer.baseUri.port" -> stubPort,
    "business-tax-account.host" -> s"http://$stubHost:$stubPort",
    "company-auth.host" -> s"http://$stubHost:$stubPort",
    "contact-frontend.host" -> s"http://$stubHost:$stubPort",
    "personal-tax-account.host" -> s"http://$stubHost:$stubPort",
    "two-step-verification.host" -> s"http://$stubHost:$stubPort",
    "two-step-verification-required.host" -> s"http://$stubHost:$stubPort",
    "tax-account-router.host" -> "",
    "throttling.enabled" -> false,
    "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
    "business-enrolments" -> "enr1,enr2",
    // The request timeout must be less than the value used in the wiremock stubs that use withFixedDelay to simulate network problems.
    "ws.timeout.request" -> 1000,
    "ws.timeout.connection" -> 500,
    "two-step-verification.enabled" -> true,
    "logger.application" -> "ERROR",
    "logger.connector" -> "ERROR",
    "some-enrolment-category" -> "enr3,enr4",
    "two-step-verification.user-segment.sa.throttle.default" -> throttle,
    "some-rule" -> rule,
    "two-step-verification.uplift-locations" -> "location-1",
    "locations" -> locations
  ) ++ Seq("auth", "cachable.short-lived-cache", "government-gateway", "sa", "user-details", "platform-analytics")
    .map(service => Map(
      s"microservice.services.$service.host" -> stubHost,
      s"microservice.services.$service.port" -> stubPort
    )).reduce(_ ++ _)

  override lazy val app = FakeApplication(additionalConfiguration = additionalConfiguration)

  feature("Router audit two step verification") {
    scenario("when there is an applicable rule and registration is optional for an admin") {

      Given("a user logged in through Government Gateway not registered for 2SV")
      createStubs(TaxAccountUser(isRegisteredFor2SV = false, oid = optionalOid))

      And("user is admin")
      stubUserDetails(credentialRole = user)

      And("the user has some active enrolments")
      val userEnrolments = stubActiveEnrolments("enr3", "enr4")

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedDetail = Json.obj(
        "userEnrolments" -> Json.parse(s"[$userEnrolments]"),
        "credentialRole" -> "User",
        "ruleApplied" -> "rule_sa",
        "mandatory" -> "false"
      )
      val expectedTransactionName = "no two step verification"
      verifyAuditEvent(auditEventStub, expectedDetail, expectedTransactionName)
    }
  }

  feature("Router audit two step verification") {
    scenario("when there is an applicable rule and registration is mandatory for an admin") {

      Given("a user logged in through Government Gateway not registered for 2SV")
      createStubs(TaxAccountUser(isRegisteredFor2SV = false, oid = mandatoryOid))

      And("user is admin")
      stubUserDetails(credentialRole = user)

      And("the user has some active enrolments")
      val userEnrolments = stubActiveEnrolments("enr3", "enr4")

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedDetail = Json.obj(
        "userEnrolments" -> Json.parse(s"[$userEnrolments]"),
        "credentialRole" -> "User",
        "ruleApplied" -> "rule_sa",
        "mandatory" -> "true"
      )
      val expectedTransactionName = "no two step verification"
      verifyAuditEvent(auditEventStub, expectedDetail, expectedTransactionName)
    }
  }

  def toJson(map: mutableMap[String, String]) = Json.obj(map.map { case (k, v) => k -> Json.toJsFieldJsValueWrapper(v) }.toSeq: _*)

  def verifyAuditEvent(auditEventStub: RequestPatternBuilder, expectedDetail: JsValue, expectedTransactionName: String): Unit = {
    val loggedRequests = WireMock.findAll(auditEventStub).asScala.toList

    def allEventsWithType(auditType: String) = loggedRequests
      .filter(s => s.getBodyAsString.matches("""^.*"auditType"[\s]*\:[\s]*"""" + auditType + """".*$"""))

    val twoStepVerificationEvents = allEventsWithType("TwoStepVerificationOutcome")
    withClue("There is no event with type TwoStepVerificationOutcome") {
      twoStepVerificationEvents should not be empty
    }

    val event = Json.parse(twoStepVerificationEvents.head.getBodyAsString)
    (event \ "tags" \ "transactionName").as[String] shouldBe expectedTransactionName
    (event \ "detail") shouldBe expectedDetail
  }
}