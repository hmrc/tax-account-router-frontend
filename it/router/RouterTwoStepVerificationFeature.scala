package router

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.test.FakeApplication
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}
import uk.gov.hmrc.domain.{Nino, SaUtr, Vrn}
import uk.gov.hmrc.play.frontend.auth.connectors.domain._

class RouterTwoStepVerificationFeature extends StubbedFeatureSpec with CommonStubs {

  scenario("a BTA eligible admin with SAUTR and not registered for 2SV should be redirected to optional 2SV registration with continue url BTA") {

    Given("a user logged in through Government Gateway")
    val saUtr = "12345"
    val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
    createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))
    stubUserDetails(credentialRole = user)

    And("the user has self assessment enrolments")
    stubSelfAssessmentEnrolments()

    And("the user has no previous returns")
    stubSaReturnWithNoPreviousReturns(saUtr)

    createStubs(TwoSVOptionalRegistrationStubPage)

    When("the user hits the router")
    go(RouterRootPath)

    Then("the user should be routed to 2SV Optional Registration Page with continue to BTA")
    on(TwoSVOptionalRegistrationPage)

    And("the authority object should be fetched once for AuthenticatedBy and once by 2SV")
    verify(2, getRequestedFor(urlEqualTo("/auth/authority")))
    verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))

    And("user's enrolments should be fetched from Auth")
    verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

    And("user's details should be fetched from User Details")
    verify(1, getRequestedFor(urlEqualTo("/user-details-uri")))

    And("sa returns should be fetched from Sa micro service")
    verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
  }

  scenario("a BTA eligible assistant with SAUTR and not registered for 2SV should be redirected to optional 2SV registration with continue url BTA") {

    Given("a user logged in through Government Gateway")
    val saUtr = "12345"
    val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
    createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))
    stubUserDetails(credentialRole = assistant)

    And("the user has self assessment enrolments")
    stubSelfAssessmentEnrolments()

    And("the user has no previous returns")
    stubSaReturnWithNoPreviousReturns(saUtr)

    createStubs(TwoSVOptionalRegistrationStubPage)

    When("the user hits the router")
    go(RouterRootPath)

    Then("the user should be routed to 2SV Optional Registration Page with continue to BTA")
    on(TwoSVOptionalRegistrationPage)

    And("the authority object should be fetched once for AuthenticatedBy and once by 2SV")
    verify(2, getRequestedFor(urlEqualTo("/auth/authority")))
    verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))

    And("user's enrolments should be fetched from Auth")
    verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

    And("user's details should be fetched from User Details")
    verify(1, getRequestedFor(urlEqualTo("/user-details-uri")))

    And("sa returns should be fetched from Sa micro service")
    verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
  }

  scenario("a BTA eligible admin user with SAUTR and VAT and not registered for 2SV should be redirected to set up extra security page") {

    Given("a user logged in through Government Gateway")
    val saUtr = "12345"
    val vrn = "45678"
    val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))), vat = Some(VatAccount("", Vrn(vrn))))
    createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))

    And("the user is an admin")
    stubUserDetails(credentialRole = user)

    And("the user has self assessment and vat enrolments")
    stubSelfAssessmentAndVatEnrolments()

    And("the user has no previous returns")
    stubSaReturnWithNoPreviousReturns(saUtr)

    createStubs(SetupExtraSecurityStubPage)

    When("the user hits the router")
    go(RouterRootPath)

    Then("the user should be routed to Set Up Extra Security Page")
    on(SetupExtraSecurityPage)

    And("the authority object should be fetched once for AuthenticatedBy and once by 2SV")
    verify(2, getRequestedFor(urlEqualTo("/auth/authority")))
    verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))

    And("user's enrolments should be fetched from Auth")
    verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

    And("user's details should be fetched from User Details")
    verify(getRequestedFor(urlEqualTo("/user-details-uri")))

    And("sa returns should be fetched from Sa micro service")
    verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
  }

  scenario("a BTA eligible assistant user with SAUTR and VAT and not registered for 2SV should be redirected to BTA home page") {

    Given("a user logged in through Government Gateway")
    val saUtr = "12345"
    val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
    createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))

    And("the user is an assistant")
    stubUserDetails(credentialRole = assistant)

    And("the user has self assessment and vat enrolments")
    stubSelfAssessmentAndVatEnrolments()

    And("the user has no previous returns")
    stubSaReturnWithNoPreviousReturns(saUtr)

    createStubs(BtaHomeStubPage)

    When("the user hits the router")
    go(RouterRootPath)

    Then("the user should be routed to BTA Home Page")
    on(BtaHomePage)

    And("the authority object should be fetched once for AuthenticatedBy and once by 2SV")
    verify(2, getRequestedFor(urlEqualTo("/auth/authority")))
    verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))

    And("user's enrolments should be fetched from Auth")
    verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

    And("user's details should be fetched from User Details")
    verify(getRequestedFor(urlEqualTo("/user-details-uri")))

    And("sa returns should be fetched from Sa micro service")
    verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
  }

  scenario("a BTA eligible user with NINO and not registered for 2SV should be redirected to 2SV with continue url BTA") {

    Given("a user logged in through Government Gateway")
    val accounts = Accounts(paye = Some(PayeAccount("link", Nino("CS100700A"))))
    createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))
    stubUserDetails()

    And("the user has self assessment enrolments")
    stubSelfAssessmentEnrolments()

    createStubs(TwoSVOptionalRegistrationStubPage)

    When("the user hits the router")
    go(RouterRootPath)

    Then("the user should be routed to 2SV Prompt Page with continue to BTA")
    on(TwoSVOptionalRegistrationPage)

    And("the authority object should be fetched once for AuthenticatedBy and once by 2SV")
    verify(2, getRequestedFor(urlEqualTo("/auth/authority")))
    verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))

    And("user's enrolments should be fetched from Auth")
    verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

    And("user's details should be fetched from User Details")
    verify(1, getRequestedFor(urlEqualTo("/user-details-uri")))

    And("Sa micro service should not be invoked")
    verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
  }

  scenario("a BTA eligible user with NINO not registered for 2SV but already has strong credentials should be redirected to BTA") {

    Given("a user logged in through Government Gateway")
    val accounts = Accounts(paye = Some(PayeAccount("link", Nino("CS100700A"))))
    createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false, credentialStrength = CredentialStrength.Strong))

    And("the user has self assessment enrolments")
    stubSelfAssessmentEnrolments()

    createStubs(BtaHomeStubPage)

    When("the user hits the router")
    go(RouterRootPath)

    Then("the user should be routed to BTA Home Page")
    on(BtaHomePage)

    And("the authority object should be fetched once for AuthenticatedBy")
    verify(getRequestedFor(urlEqualTo("/auth/authority")))
    verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))

    And("user's enrolments should be fetched from Auth")
    verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

    And("user's details should not be fetched from User Details")
    verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

    And("Sa micro service should not be invoked")
    verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
  }
}

class RouterFeatureForMandatoryRegistration extends StubbedFeatureSpec with CommonStubs {

  override lazy val app = FakeApplication(
    additionalConfiguration = config +("two-step-verification.user-segment.sa.throttle.default" -> "1000", "two-step-verification.user-segment.sa_vat.throttle.default" -> "1000")
  )

  feature("Router feature") {
    scenario("a BTA eligible admin with SAUTR and not registered for 2SV should be redirected to mandatory 2SV registration with continue url BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))
      stubUserDetails(credentialRole = user)

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has no previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      createStubs(TwoSVMandatoryRegistrationStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to 2SV Mandatory Registration Page with continue to BTA")
      on(TwoSVMandatoryRegistrationPage)

      And("the authority object should be fetched once for AuthenticatedBy and once by 2SV")
      verify(2, getRequestedFor(urlEqualTo("/auth/authority")))
      verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should be fetched once from User Details")
      verify(1, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a BTA eligible assistant with SAUTR and not registered for 2SV should be redirected to mandatory 2SV registration with continue url BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))
      stubUserDetails(credentialRole = user)

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has no previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      createStubs(TwoSVMandatoryRegistrationStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to 2SV Mandatory Registration Page with continue to BTA")
      on(TwoSVMandatoryRegistrationPage)

      And("the authority object should be fetched once for AuthenticatedBy and once by 2SV")
      verify(2, getRequestedFor(urlEqualTo("/auth/authority")))
      verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should be fetched once from User Details")
      verify(1, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a BTA eligible admin user with SAUTR and VAT and not registered for 2SV should be redirected to set up extra security page") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))

      And("the user is an assistant")
      stubUserDetails(credentialRole = user)

      And("the user has self assessment and vat enrolments")
      stubSelfAssessmentAndVatEnrolments()

      And("the user has no previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      createStubs(SetupExtraSecurityStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to Set Up Extra Security Page")
      on(SetupExtraSecurityPage)

      And("the authority object should be fetched once for AuthenticatedBy and once by 2SV")
      verify(2, getRequestedFor(urlEqualTo("/auth/authority")))
      verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should be fetched from User Details")
      verify(getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }

    scenario("a BTA eligible assistant user with SAUTR and VAT and not registered for 2SV should be redirected to BTA") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))

      And("the user is an assistant")
      stubUserDetails(credentialRole = assistant)

      And("the user has self assessment and vat enrolments")
      stubSelfAssessmentAndVatEnrolments()

      And("the user has no previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      createStubs(BtaHomeStubPage)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be routed to BTA Home Page")
      on(BtaHomePage)

      And("the authority object should be fetched once for AuthenticatedBy and once by 2SV")
      verify(2, getRequestedFor(urlEqualTo("/auth/authority")))
      verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should be fetched from User Details")
      verify(getRequestedFor(urlEqualTo("/user-details-uri")))

      And("sa returns should be fetched from Sa micro service")
      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }
  }
}