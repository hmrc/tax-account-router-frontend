package acceptance

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.test.FakeApplication
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, SaAccount}

class RouterFeature extends StubbedFeatureSpec with CommonStubs {

  val enrolmentConfiguration = Map[String, Any](
        "business-enrolments" -> List("enr1", "enr2"),
        "self-assessment-enrolments" -> List("enr3", "enr4")
      )

  override lazy val app = FakeApplication(additionalConfiguration = config ++ enrolmentConfiguration)

  feature("Router feature") {

    scenario("a user logged in through Verify should be redirected to PTA") {

      Given("a user logged in through Verify")
      createStubs(TaxAccountUser(firstTimeLoggedIn = true, tokenPresent = false))

      And("a stubbed PTA homepage")
      createStubs(PtaHomeStubPage)

      And("the welcome page has already been visited")
      stubSave4LaterWelcomePageSeen()

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to PTA Home Page")
      on(PtaHomePage)
    }

    scenario("a user logged in through GG with any business account be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      createStubs(TaxAccountUser())

      And("with business related enrolments")
      stubProfileWithBusinessEnrolments()

      And("a stubbed BTA homepage")
      createStubs(BtaHomeStubPage)

      And("the welcome page has already been visited")
      stubSave4LaterWelcomePageSeen()

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the user profile should be fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))
    }

    scenario("a user logged in through GG with self assessment enrolments and no previous returns should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("with self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("with previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      And("a stubbed BTA homepage")
      createStubs(BtaHomeStubPage)

      And("the welcome page has already been visited")
      stubSave4LaterWelcomePageSeen()

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the user profile should be fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/last-return")))
    }

    scenario("a user logged in through GG with self assessment enrolments and in a partnership should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("with self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("with previous returns")
      stubSaReturnWithPartnership(saUtr)

      And("a stubbed BTA homepage")
      createStubs(BtaHomeStubPage)

      And("the welcome page has already been visited")
      stubSave4LaterWelcomePageSeen()

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the user profile should be fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/last-return")))
    }
  }
}
