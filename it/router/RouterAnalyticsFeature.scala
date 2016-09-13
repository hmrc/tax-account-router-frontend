package router

import connector.{AnalyticsData, GaEvent}
import play.api.test.FakeApplication
import support.page._
import support.stubs.PlatformAnalyticsStub.verifyAnalytics
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, SaAccount}

class RouterAnalyticsFeature extends StubbedFeatureSpec with CommonStubs {

  override lazy val app = FakeApplication(
    additionalConfiguration = config + ("two-step-verification.user-segment.sa.throttle.default" -> "1000")
  )

  feature("Router analytics feature") {

    scenario("send analytics details for mandatory 2sv registration journey") {

      Given("User has google analytics cookie in browser")

      And("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))
      stubUserDetails()

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has no previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      createStubs(TwoSVMandatoryRegistrationStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to 2SV Mandatory Registration Page with continue to BTA")
      on(TwoSVMandatoryRegistrationPage)

      And("analytic details were sent to google")
      verifyAnalytics(
        AnalyticsData("GA1.4.405633776.1470748420", List(
          GaEvent("routing", "two-step-verification", "bta-home-page-for-user-with-no-previous-return", Nil),
          GaEvent("sos_b2sv_registration_route", "rule_sa", "mandatory", Nil)
        ))
      )
    }
  }
}
