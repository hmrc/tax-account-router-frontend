package router

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.test.FakeApplication
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, SaAccount}

class RouterSaUnresponsiveFeature extends StubbedFeatureSpec with CommonStubs {

  val additionalConfiguration = Map[String, Any](
    "business-enrolments" -> "enr1,enr2",
    "self-assessment-enrolments" -> "enr3,enr4",
    // The request timeout must be less than the value used in the wiremock stubs that use withFixedDelay to simulate network problems.
    "ws.timeout.request" -> 1000,
    "ws.timeout.connection" -> 500,
    "logger.application" -> "ERROR",
    "logger.connector" -> "ERROR"
  )

  override lazy val app = FakeApplication(additionalConfiguration = config ++ additionalConfiguration)

  feature("Router with SA unresponsive") {

    scenario("a user logged in through GG and sa is unresponsive should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the sa is unresponsive")
      stubSaReturnToProperlyRespondAfter2Seconds(saUtr)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo("/auth/authority")))

      And("the user profile should be fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a user logged in through GG and gg is unresponsive should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("gg is unresponsive")
      stubProfileToReturnAfter2Seconds()

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo("/auth/authority")))

      And("the user profile should be fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
  }
}
