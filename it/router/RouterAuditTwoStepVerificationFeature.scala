package router

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{RequestPatternBuilder, WireMock}
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeApplication
import support.page._
import support.stubs.{CommonStubs, SessionUser, StubbedFeatureSpec}

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

  val optionalUserId = "bbb"  // 81.6 (percentage calculated from hashcode)
  val mandatoryUserId = "aaa" // 49.6 (percentage calculated from hashcode)
  val throttle = 50 // registration is mandatory if the percentage is smaller than throttle value

  val additionalConfiguration = Map[String, Any](
    "auditing.consumer.baseUri.host" -> stubHost,
    "auditing.consumer.baseUri.port" -> stubPort,
    "microservice.services.auth.host" -> stubHost,
    "microservice.services.auth.port" -> stubPort,
    "company-auth.host" -> s"http://$stubHost:$stubPort",
    "tax-account-router.host" -> "",
    "two-step-verification.enabled" -> true,
    "two-step-verification.user-segment.sa.throttle.default" -> throttle
  )

  override lazy val app = FakeApplication(additionalConfiguration = additionalConfiguration)

  feature("Router audit two step verification") {
    scenario("when there is an applicable rule and registration is optional for an admin") {

      Given("a user logged in through Government Gateway not registered for 2SV")
      SessionUser(isRegisteredFor2SV = false, internalUserIdentifier = optionalUserId).stubLoggedIn()

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
      val expectedTransactionName = "two step verification optional"
      verifyAuditEvent(auditEventStub, expectedDetail, expectedTransactionName)
    }
  }

  feature("Router audit two step verification") {
    scenario("when there is an applicable rule and registration is mandatory for an admin") {

      Given("a user logged in through Government Gateway not registered for 2SV")
      SessionUser(isRegisteredFor2SV = false, internalUserIdentifier = optionalUserId).stubLoggedIn()

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
      val expectedTransactionName = "two step verification mandatory"
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