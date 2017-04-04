package router

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.test.FakeApplication
import support.page._
import support.stubs.{CommonStubs, SessionUser, StubbedFeatureSpec}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, SaAccount}

class RouterSaUnresponsiveFeature extends StubbedFeatureSpec with CommonStubs {

  val additionalConfiguration = Map[String, Any](
    "business-enrolments" -> "enr1,enr2",
    // The request timeout must be less than the value used in the wiremock stubs that use withFixedDelay to simulate network problems.
    "ws.timeout.request" -> 1000,
    "ws.timeout.connection" -> 500,
    "two-step-verification.enabled" -> true
  )

  override lazy val app = FakeApplication(additionalConfiguration = config ++ additionalConfiguration)

  feature("Router with SA unresponsive") {

    scenario("a user logged in through GG and sa is unresponsive should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      SessionUser(accounts = accounts).stubLoggedIn()

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the sa is unresponsive")
      stubSaReturnToProperlyRespondAfter2Seconds(saUtr)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      eventually {

        And("the authority object should be fetched once for AuthenticatedBy")
        verifyAuthorityObjectIsFetched()

        And("user's enrolments should be fetched from Auth")
        verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

        And("user's details should be fetched from User Details")
        verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

        And("sa returns should be fetched from Sa micro service")
        verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))

      }
    }

    scenario("a user logged in through GG and gg is unresponsive should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      SessionUser(accounts = accounts).stubLoggedIn()

      And("gg is unresponsive")
      stubEnrolmentsToReturnAfter2Seconds()

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      eventually {

        And("the authority object should be fetched once for AuthenticatedBy")
        verifyAuthorityObjectIsFetched()

        And("user's enrolments should be fetched from Auth")
        verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

        And("user's details should be fetched from User Details")
        verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

        And("Sa micro service should not be invoked")
        verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))

      }
    }
  }

  private def verifyAuthorityObjectIsFetched() = {
    verify(getRequestedFor(urlEqualTo("/auth/authority")))
    verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))
  }
}
