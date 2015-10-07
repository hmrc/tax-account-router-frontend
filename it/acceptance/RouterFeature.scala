package acceptance

import support.page.{PtaHomePage, PtaHomeStubPage, RouterHomePage}
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}

class RouterFeature extends StubbedFeatureSpec with CommonStubs {

  feature("Router feature") {

    scenario("a user logged in through Verify should be redirected to PTA") {

      Given("a user logged in through Verify")
      createStubs(TaxAccountUser(firstTimeLoggedIn = true, tokenPresent = false))

      And("a stubbed PTA homepage")
      createStubs(PtaHomeStubPage)

      And("the welcome page has already been visited")
      stubSave4LaterWelcomePageSeen()

      When("the user hits the router")
      go(RouterHomePage)

      Then("the user should be routed to PTA Home Page")
      on(PtaHomePage)
    }
  }
}
