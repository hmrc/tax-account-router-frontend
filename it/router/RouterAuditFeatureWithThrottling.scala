package router

import com.github.tomakehurst.wiremock.client.WireMock.{postRequestedFor, urlMatching, verify}
import engine.RoutingReason.IS_A_VERIFY_USER
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor5, Tables}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import support.page._
import support.stubs._

class RouterAuditFeatureWithThrottling extends StubbedFeatureSpec with CommonStubs with AuditTools {
  override lazy val app: Application = new GuiceApplicationBuilder().configure(
    config + (
      "throttling.enabled" -> true,
      "throttling.locations.personal-tax-account-verify.percentageBeToThrottled" -> "50",
      "throttling.locations.personal-tax-account-verify.fallback" -> "business-tax-account"
    )
  ).build()

  val scenarios: TableFor5[String, Stub, WebPage, Boolean, String] = Tables.Table[String, Stub, WebPage, Boolean, String](
    ("scenario", "stubPage", "expectedDestination", "expectedThrottledFlag", "expectedTransactionName"),
    ("route to original destination when not throttled", PtaHomeStubPage, PtaHomePage, false, "sent to personal tax account")
  )

  TableDrivenPropertyChecks.forAll(scenarios) { (scenarioName, stubPage, expectedDestination, expectedThrottledFlag, expectedTransactionName) =>

    scenario(scenarioName) {

      Given("a user logged in through Verify supposed to go to PTA")
      setVerifyUser()

      val auditEventStub = auditEventPattern()

      createStubs(stubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(expectedDestination)

      And("user is sent to BTA an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (IS_A_VERIFY_USER.key -> "true"))
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = true,
        percentage = "50",
        throttled = expectedThrottledFlag,
        destinationUrlBeforeThrottling = "/personal-account",
        destinationNameBeforeThrottling = "personal-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "pta-home-page-for-verify-user", expectedThrottlingDetails)
    }
  }
}
