package router

import connector.{AnalyticsData, GaEvent}
import play.api.test.FakeApplication
import support.page.{RouterRootPath, TwoSVMandatoryRegistrationPage, TwoSVMandatoryRegistrationStubPage}
import support.stubs.PlatformAnalyticsStub.verifyAnalytics
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, SaAccount}

class RouterAnalyticsFeature extends StubbedFeatureSpec with CommonStubs {
  val additionalConfiguration = Map[String, Any](
    "business-enrolments" -> "enr1,enr2",
    "self-assessment-enrolments" -> "enr3,enr4",
    // The request timeout must be less than the value used in the wiremock stubs that use withFixedDelay to simulate network problems.
    "ws.timeout.request" -> 10000,
    "ws.timeout.connection" -> 6000,
    "two-step-verification.enabled" -> true,
    "logger.application" -> "ERROR",
    "logger.connector" -> "ERROR"
  )

  override lazy val app = FakeApplication(
    additionalConfiguration = config ++ PlayConfig.additionalConfiguration +
      ("two-step-verification.throttle.default" -> "1000")
  )


  feature("Router analytics feature") {

    scenario("a BTA eligible user with SAUTR and not registered for 2SV should be redirected to mandatory 2SV registration with continue url BTA and analytics details should be sent appropriately to google") {

      Given("User has google analytics cookie in browser")

      And("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      createStubs(TwoSVMandatoryRegistrationStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to 2SV Mandatory Registration Page with continue to BTA")
      on(TwoSVMandatoryRegistrationPage)

      And("analytic details were sent to google")
      verifyAnalytics(AnalyticsData("GA1.4.405633776.1470748420", List(GaEvent("routing", "two-step-verification", "bta-home-page-for-user-with-no-previous-return"))))
    }
  }
}
