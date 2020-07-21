package router

import connector.{AnalyticsData, GaEvent}
import play.api.test.FakeApplication
import support.page._
import support.stubs.PlatformAnalyticsStub.verifyAnalytics
import support.stubs.{CommonStubs, SessionUser, StubbedFeatureSpec}


class RouterAnalyticsFeature extends StubbedFeatureSpec with CommonStubs {

  feature("Router analytics feature") {

    scenario("send analytics details for mandatory 2sv registration journey") {

      Given("User has google analytics cookie in browser")

      And("a user logged in through Government Gateway")
      val saUtr = "12345"
      //val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      val accounts = ???
//      SessionUser(accounts = accounts, isRegisteredFor2SV = false).stubLoggedIn()
//      stubUserDetails()

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has no previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA home page")
      on(BtaHomePage)

      And("analytic details were sent to google")
      verifyAnalytics(
        AnalyticsData("GA1.4.405633776.1470748420", List(
          GaEvent("routing", "business-tax-account", "bta-home-page-for-user-with-no-previous-return", Nil)
        ))
      )
    }
  }
}
