package router

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec}


class RouterSaUnresponsiveFeature extends StubbedFeatureSpec with CommonStubs {

  val additionalConfiguration: Map[String, Any] = Map[String, Any](
    "business-enrolments" -> "enr1,enr2",
    // The request timeout must be less than the value used in the wiremock stubs that use withFixedDelay to simulate network problems.
    "ws.timeout.request" -> 1000,
    "ws.timeout.connection" -> 500,
    "two-step-verification.enabled" -> true
  )

  override lazy val app: Application = new GuiceApplicationBuilder().configure(config ++ additionalConfiguration).build()

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
      stubRetrievalAuthorisedEnrolments()
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
