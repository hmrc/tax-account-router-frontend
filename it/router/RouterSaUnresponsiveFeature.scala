package router

import com.github.tomakehurst.wiremock.client.WireMock._
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec}


class RouterSaUnresponsiveFeature extends StubbedFeatureSpec with CommonStubs {

  feature("Router with SA unresponsive") {

    scenario("a user logged in through GG, SA is unresponsive, user should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()

      And("the SA is unresponsive")
      stubRetrievalSAUTR(responsive = false)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      And("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("user's details should be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))
    }

    scenario("a user logged in through GG, GG is unresponsive, user should be redirected to BTA") {

      Given("a user logged in through Government Gateway but GG is unresponsive")
      stubAuthenticatedUser()
      stubRetrievalInternalId()
      stubRetrievalALLEnrolments(hasEnrolments = false)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      And("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("user's details should be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("SA micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
  }

}
