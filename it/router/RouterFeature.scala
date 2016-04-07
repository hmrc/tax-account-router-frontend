package router

import com.github.tomakehurst.wiremock.client.WireMock._
import connector.AffinityGroupValue
import play.api.test.FakeApplication
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, CredentialStrength, PayeAccount, SaAccount}

class RouterFeature extends StubbedFeatureSpec with CommonStubs {

  val additionalConfiguration = Map[String, Any](
    "business-enrolments" -> "enr1,enr2",
    "self-assessment-enrolments" -> "enr3,enr4",
    "ws.timeout.request" -> 1000,
    "ws.timeout.connection" -> 500,
    "two-step-verification.enabled" -> true,
    "logger.application" -> "ERROR",
    "logger.connector" -> "ERROR"
  )

  override lazy val app = FakeApplication(additionalConfiguration = config ++ additionalConfiguration)

  feature("Router feature") {

    scenario("a user logged in through Verify should be redirected to PTA") {

      Given("a user logged in through Verify")
      createStubs(TaxAccountUser(tokenPresent = false))

      createStubs(PtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to PTA Home Page")
      on(PtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo("/auth/authority")))

      And("Government Gateway service should not be invoked")
      verify(0, getRequestedFor(urlEqualTo("/profile")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }

    scenario("a user logged in through GG with any business account be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      createStubs(TaxAccountUser())

      And("the user has business related enrolments")
      stubProfileWithBusinessEnrolments()

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

    scenario("a user logged in through GG with self assessment enrolments and no previous returns should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the user has previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

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

    scenario("a user logged in through GG and sa returning 500 should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the sa is returning 500")
      stubSaReturnToReturn500(saUtr)

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

    scenario("a user logged in through GG and gg returning 500 should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("gg is returning 500")
      stubProfileToReturn500()

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

    scenario("a user logged in through GG with self assessment enrolments and in a partnership should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the user is in a partnership")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("partnership"))

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

    scenario("a user logged in through GG with self assessment enrolments and self employed should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the user is self employed")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("self_employment"))

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

    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with no NINO should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))), paye = None)
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the user has previous returns and is not in a partnership and is not self employed and has no NINO")
      stubSaReturn(saUtr, previousReturns = true)

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

    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with NINO should be redirected to PTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))), paye = Some(PayeAccount("link", Nino("CS100700A"))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the user has previous returns and is not in a partnership and is not self employed and has NINO")
      stubSaReturn(saUtr, previousReturns = true)

      createStubs(PtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to PTA Home Page")
      on(PtaHomePage)

      And("the user profile should be fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a BTA eligible user with SAUTR and not registered for 2SV should be redirected to 2SV with continue url BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the user has previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      createStubs(TwoSVPromptStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to 2SV Prompt Page with continue to BTA")
      on(TwoSVPromptPage)

      And("the authority object should be fetched once for AuthenticatedBy and once by 2SV")
      verify(2, getRequestedFor(urlEqualTo("/auth/authority")))

      And("the user profile should be fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a BTA eligible user with NINO and not registered for 2SV should be redirected to 2SV with continue url BTA") {

      Given("a user logged in through Government Gateway")
      val accounts = Accounts(paye = Some(PayeAccount("link", Nino("CS100700A"))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      createStubs(TwoSVPromptStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to 2SV Prompt Page with continue to BTA")
      on(TwoSVPromptPage)

      And("the authority object should be fetched once for AuthenticatedBy and once by 2SV")
      verify(2, getRequestedFor(urlEqualTo("/auth/authority")))

      And("the user profile should be fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }

    scenario("a BTA eligible user with NINO, not registered for 2SV but already has strong credentials should be not redirected to 2SV with continue url BTA") {

      Given("a user logged in through Government Gateway")
      val accounts = Accounts(paye = Some(PayeAccount("link", Nino("CS100700A"))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false, credentialStrength = CredentialStrength.Strong))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

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

    scenario("a user logged in through GG and has no sa and no business enrolment with individual affinity group and inactive enrolments should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      createStubs(TaxAccountUser(affinityGroup = AffinityGroupValue.INDIVIDUAL))

      And("the user has an inactive enrolment and individual affinity group")
      stubProfileWithInactiveEnrolmentsAndIndividualAffinityGroup()

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

    scenario("a user logged in through GG and has no sa and no business enrolment with individual affinity group and no inactive enrolments should be redirected to PTA") {

      Given("a user logged in through Government Gateway")
      createStubs(TaxAccountUser(affinityGroup = AffinityGroupValue.INDIVIDUAL))

      And("the user has an no inactive enrolment and individual affinity group")
      stubProfileWithNoEnrolments(affinityGroup = AffinityGroupValue.INDIVIDUAL)

      createStubs(PtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to PTA Home Page")
      on(PtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy")
      verify(getRequestedFor(urlEqualTo("/auth/authority")))

      And("the user profile should be fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
  }
}
