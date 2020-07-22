package router

import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec}


class RouterAnalyticsFeature extends StubbedFeatureSpec with CommonStubs {

  feature("Router analytics feature") {

    scenario("send analytics details for mandatory 2sv registration journey") {

      Given("User has google analytics cookie in browser")

      And("a user logged in through Verify")

      And("the user has self assessment enrolments")
      setVerifyUser()

      And("the user has no previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      createStubs(PtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to PTA home page")
      on(PtaHomePage)

    }
  }
}
