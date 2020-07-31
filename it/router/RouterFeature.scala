package router

import com.github.tomakehurst.wiremock.client.WireMock._
import model.AffinityGroupValue
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec}


class RouterFeature extends StubbedFeatureSpec with CommonStubs {

  feature("Router feature") {

    scenario("a user logged in through Verify should be redirected to PTA") {

      Given("a user logged in through Verify")
      setVerifyUser()

      createStubs(PtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to PTA Home Page")
      on(PtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")

      And("user's enrolments should not be fetched from Auth")
      verify(0, getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }

    scenario("a user logged in through GG with any business account be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has business related enrolments")
      stubRetrievalALLEnrolments("enr1")

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }

    scenario("a user logged in through GG with self assessment enrolments and no previous returns should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      And("the user has no previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG and sa returning 500 should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      And("the sa is returning 500")
      stubSaReturnToReturn500(saUtr)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG and Auth returning 500 on GET enrolments should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("gg is returning 500")
      stubRetrievalALLEnrolments(responsive = false)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }

    scenario("a user logged in through GG with self assessment enrolments and in a partnership should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      And("the user is in a partnership")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("partnership"))

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG with self assessment enrolments and self employed should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      And("the user is self employed")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("self_employment"))

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with no NINO should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      And("the user has previous returns and is not in a partnership and is not self employed and has no NINO")
      stubSaReturn(saUtr, previousReturns = true)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with NINO should be redirected to PTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()
      stubRetrievalNINO()

      And("the user has previous returns and is not in a partnership and is not self employed and has NINO")
      stubSaReturn(saUtr, previousReturns = true)

      createStubs(PtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to PTA Home Page")
      on(PtaHomePage)

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG and has no sa and no business enrolment with individual affinity group and inactive enrolments should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has an inactive enrolment and individual affinity group")
      stubRetrievalALLEnrolments()
      stubRetrievalAffinityGroup(AffinityGroupValue.INDIVIDUAL)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }

    scenario("a user logged in through GG and has no sa and no business enrolment with individual affinity group and no inactive enrolments should be redirected to PTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has no inactive enrolments and individual affinity group")
      stubRetrievalALLEnrolments(hasEnrolments = false)
      stubRetrievalAffinityGroup(AffinityGroupValue.INDIVIDUAL)

      createStubs(PtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to PTA Home Page")
      on(PtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }

    scenario("a user logged in through GG and has no sa and no business enrolment and no inactive enrolments and affinity group not available should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has no inactive enrolments and affinity group is not available")
      stubRetrievalALLEnrolments(hasEnrolments = false)
      stubRetrievalAffinityGroup(ready = false)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to PTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
  }

  scenario("a user logged in through One Time Login or Privileged Access with no enrolments should go to BTA") {

    Given("a user logged in through One Time Login or Privileged Access")
    stubRetrievalInternalId()

    And("the user has no inactive enrolments")
    stubRetrievalALLEnrolments(hasEnrolments = false)

    createStubs(BtaHomeStubPage)

    When("the user hits the router")
    go(RouterRootPath)

    Then("the user should be routed to BTA Home Page")
    on(BtaHomePage)

    And("user's details should not be fetched from User Details")
    verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

    And("Sa micro service should not be invoked")
    verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
  }

}