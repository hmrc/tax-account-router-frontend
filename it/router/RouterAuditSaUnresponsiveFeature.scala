package router

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import engine.AuditInfo
import engine.RoutingReason._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec}

import scala.collection.JavaConverters._

class RouterAuditSaUnresponsiveFeature extends StubbedFeatureSpec with CommonStubs {

  val emptyRoutingReasons: Map[String, String] = AuditInfo.emptyReasons.map { case (k, _) => k.key -> "-" }

  val additionalConfiguration: Map[String, Any] = Map[String, Any](
    "business-enrolments" -> "enr1,enr2",
    // The request timeout must be less than the value used in the wiremock stubs that use withFixedDelay to simulate network problems.
    "ws.timeout.request" -> 1000,
    "ws.timeout.connection" -> 500,
    "two-step-verification.enabled" -> true
  )

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config ++ additionalConfiguration).build()

  feature("Router audit with SA unresponsive") {

    scenario("a user logged in through GG and sa is unresponsive should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      And("the user has previous returns")
      stubSaReturnToProperlyRespondAfter2Seconds(saUtr)

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "false"
        ))
      val expectedTransactionName = "sent to business tax account"
      eventually {
        verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-no-previous-return")
      }
    }

    scenario("a user logged in through GG and GG is unresponsive should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments(responsive = false)

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "false"
        ))
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-gg-unavailable")
    }
  }

  def toJson(map: Map[String, String]): JsObject = Json.obj(map.map { case (k, v) => k -> Json.toJsFieldJsValueWrapper(v) }.toSeq: _*)

  def verifyAuditEvent(auditEventStub: RequestPatternBuilder, expectedReasons: JsValue, expectedTransactionName: String, ruleApplied: String): Unit = {
    val loggedRequests = WireMock.findAll(auditEventStub).asScala.toList
    val event = Json.parse(loggedRequests
      .filter(s => s.getBodyAsString.matches( """^.*"auditType"[\s]*\:[\s]*"Routing".*$""")).head.getBodyAsString)
    (event \ "tags" \ "transactionName").as[String] shouldBe expectedTransactionName
    (event \ "detail" \ "ruleApplied").as[String] shouldBe ruleApplied
    (event \ "detail" \ "reasons").get shouldBe expectedReasons
  }
}
