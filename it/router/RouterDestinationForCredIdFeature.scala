package router

import com.github.tomakehurst.wiremock.client.WireMock._
import controllers.Destination
import play.api.test.FakeApplication
import support.page._
import support.stubs.{CommonStubs, SessionUser, StubbedFeatureSpec}

// TODO: duplicated testcases, same as in RouterFeature. Maybe it can be defined in one place only
class RouterDestinationForCredIdFeature extends StubbedFeatureSpec with CommonStubs {

  override lazy val app = FakeApplication(additionalConfiguration = config)

  val credId = "cred-id-for-the-user"

  import scala.concurrent.duration._
  import scala.concurrent.{Await, Future}

  implicit val defaultTimeout = 5 seconds

  def await[A](future: Future[A])(implicit timeout: Duration) = Await.result(future, timeout)

  private val destinationPTA = Destination("http://localhost:11111/personal-account")
  private val destinationBTA = Destination("http://localhost:11111/b-account")

  feature("Router feature") {

//    scenario("a user should be redirected to PTA") {
//
//      Given("a user with cred id exists")
//      SessionUser(isRegisteredFor2SV = false, internalUserIdentifier = credId).stubLoggedOut()
//
//      When("the destination for cred id is fetched")
//      val response = await(Navigation.goToDestination(s"/account/destination/$credId")(app).get())
//      response.status shouldBe 200
//
//      Then("the routing should return PTA Home Page")
//      response.json.as[Destination] shouldBe destinationPTA
//
//      And("the authority object should be fetched by credId")
//      verifyAuthorityObjectIsFetchedByCredId(credId)
//
//      And("user's enrolments should not be fetched from Auth")
//      verify(0, getRequestedFor(urlEqualTo("/auth/enrolments-uri")))
//
//      And("user's details should not be fetched from User Details")
//      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))
//
//      And("Sa micro service should not be invoked")
//      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
//    }

    scenario("a user with any business account be redirected to BTA") {

      Given("a user with cred id exists")
      SessionUser(isRegisteredFor2SV = false, internalUserIdentifier = credId).stubLoggedOut()

      And("the user has business related enrolments")
      stubBusinessEnrolments()

      When("the destination for cred id is fetched")
      val response = await(Navigation.goToDestination(s"/account/destination/$credId")(app).get())
      response.status shouldBe 200

      Then("the routing should return BTA Home Page")
      response.json.as[Destination] shouldBe destinationBTA

      And("the authority object should be fetched by credId")
      verifyAuthorityObjectIsFetchedByCredId(credId)

      And("user's enrolments should be fetched from Auth")
      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      And("user's details should not be fetched from User Details")
      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      And("Sa micro service should not be invoked")
      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
  }

  //
  //    scenario("a user logged in through GG with self assessment enrolments and no previous returns should be redirected to BTA") {
  //
  //      Given("a user logged in through Government Gateway")
  //      val saUtr = "12345"
  //      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
  //      createStubs(TaxAccountUser(accounts = accounts))
  //
  //      And("the user has self assessment enrolments")
  //      stubSelfAssessmentEnrolments()
  //
  //      And("the user has no previous returns")
  //      stubSaReturnWithNoPreviousReturns(saUtr)
  //
  //      createStubs(BtaHomeStubPage)
  //
  //      When("the user hits the router")
  //      go(RouterRootPath)
  //
  //      Then("the user should be routed to BTA Home Page")
  //      on(BtaHomePage)
  //
  //      And("the authority object should be fetched once for AuthenticatedBy")
  //      verifyAuthorityObjectIsFetchedByCredId()
  //
  //      And("user's enrolments should be fetched from Auth")
  //      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))
  //
  //      And("user's details should not be fetched from User Details")
  //      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))
  //
  //      And("sa returns should be fetched from Sa micro service")
  //      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
  //    }
  //
  //    scenario("a user logged in through GG and sa returning 500 should be redirected to BTA") {
  //
  //      Given("a user logged in through Government Gateway")
  //      val saUtr = "12345"
  //      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
  //      createStubs(TaxAccountUser(accounts = accounts))
  //
  //      And("the user has self assessment enrolments")
  //      stubSelfAssessmentEnrolments()
  //
  //      And("the sa is returning 500")
  //      stubSaReturnToReturn500(saUtr)
  //
  //      createStubs(BtaHomeStubPage)
  //
  //      When("the user hits the router")
  //      go(RouterRootPath)
  //
  //      Then("the user should be routed to BTA Home Page")
  //      on(BtaHomePage)
  //
  //      And("the authority object should be fetched once for AuthenticatedBy")
  //      verifyAuthorityObjectIsFetchedByCredId()
  //
  //      And("user's enrolments should be fetched from Auth")
  //      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))
  //
  //      And("user's details should not be fetched from User Details")
  //      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))
  //
  //      And("sa returns should be fetched from Sa micro service")
  //      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
  //    }
  //
  //    scenario("a user logged in through GG and Auth returning 500 on GET enrolments should be redirected to BTA") {
  //
  //      Given("a user logged in through Government Gateway")
  //      val saUtr = "12345"
  //      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
  //      createStubs(TaxAccountUser(accounts = accounts))
  //
  //      And("gg is returning 500")
  //      stubEnrolmentsToReturn500()
  //
  //      createStubs(BtaHomeStubPage)
  //
  //      When("the user hits the router")
  //      go(RouterRootPath)
  //
  //      Then("the user should be routed to BTA Home Page")
  //      on(BtaHomePage)
  //
  //      And("the authority object should be fetched once for AuthenticatedBy")
  //      verifyAuthorityObjectIsFetchedByCredId()
  //
  //      And("user's enrolments should be fetched from Auth")
  //      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))
  //
  //      And("user's details should not be fetched from User Details")
  //      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))
  //
  //      And("Sa micro service should not be invoked")
  //      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
  //    }
  //
  //    scenario("a user logged in through GG with self assessment enrolments and in a partnership should be redirected to BTA") {
  //
  //      Given("a user logged in through Government Gateway")
  //      val saUtr = "12345"
  //      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
  //      createStubs(TaxAccountUser(accounts = accounts))
  //
  //      And("the user has self assessment enrolments")
  //      stubSelfAssessmentEnrolments()
  //
  //      And("the user is in a partnership")
  //      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("partnership"))
  //
  //      createStubs(BtaHomeStubPage)
  //
  //      When("the user hits the router")
  //      go(RouterRootPath)
  //
  //      Then("the user should be routed to BTA Home Page")
  //      on(BtaHomePage)
  //
  //      And("the authority object should be fetched once for AuthenticatedBy")
  //      verifyAuthorityObjectIsFetchedByCredId()
  //
  //      And("user's enrolments should be fetched from Auth")
  //      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))
  //
  //      And("user's details should not be fetched from User Details")
  //      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))
  //
  //      And("sa returns should be fetched from Sa micro service")
  //      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
  //    }
  //
  //    scenario("a user logged in through GG with self assessment enrolments and self employed should be redirected to BTA") {
  //
  //      Given("a user logged in through Government Gateway")
  //      val saUtr = "12345"
  //      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
  //      createStubs(TaxAccountUser(accounts = accounts))
  //
  //      And("the user has self assessment enrolments")
  //      stubSelfAssessmentEnrolments()
  //
  //      And("the user is self employed")
  //      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("self_employment"))
  //
  //      createStubs(BtaHomeStubPage)
  //
  //      When("the user hits the router")
  //      go(RouterRootPath)
  //
  //      Then("the user should be routed to BTA Home Page")
  //      on(BtaHomePage)
  //
  //      And("the authority object should be fetched once for AuthenticatedBy")
  //      verifyAuthorityObjectIsFetchedByCredId()
  //
  //      And("user's enrolments should be fetched from Auth")
  //      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))
  //
  //      And("user's details should not be fetched from User Details")
  //      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))
  //
  //      And("sa returns should be fetched from Sa micro service")
  //      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
  //    }
  //
  //    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with no NINO should be redirected to BTA") {
  //
  //      Given("a user logged in through Government Gateway")
  //      val saUtr = "12345"
  //      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))), paye = None)
  //      createStubs(TaxAccountUser(accounts = accounts))
  //
  //      And("the user has self assessment enrolments")
  //      stubSelfAssessmentEnrolments()
  //
  //      And("the user has previous returns and is not in a partnership and is not self employed and has no NINO")
  //      stubSaReturn(saUtr, previousReturns = true)
  //
  //      createStubs(BtaHomeStubPage)
  //
  //      When("the user hits the router")
  //      go(RouterRootPath)
  //
  //      Then("the user should be routed to BTA Home Page")
  //      on(BtaHomePage)
  //
  //      And("the authority object should be fetched once for AuthenticatedBy")
  //      verifyAuthorityObjectIsFetchedByCredId()
  //
  //      And("user's enrolments should be fetched from Auth")
  //      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))
  //
  //      And("user's details should not be fetched from User Details")
  //      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))
  //
  //      And("sa returns should be fetched from Sa micro service")
  //      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
  //    }
  //
  //    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with NINO should be redirected to PTA") {
  //
  //      Given("a user logged in through Government Gateway")
  //      val saUtr = "12345"
  //      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))), paye = Some(PayeAccount("link", Nino("CS100700A"))))
  //      createStubs(TaxAccountUser(accounts = accounts))
  //
  //      And("the user has self assessment enrolments")
  //      stubSelfAssessmentEnrolments()
  //
  //      And("the user has previous returns and is not in a partnership and is not self employed and has NINO")
  //      stubSaReturn(saUtr, previousReturns = true)
  //
  //      createStubs(PtaHomeStubPage)
  //
  //      When("the user hits the router")
  //      go(RouterRootPath)
  //
  //      Then("the user should be routed to PTA Home Page")
  //      on(PtaHomePage)
  //
  //      And("user's enrolments should be fetched from Auth")
  //      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))
  //
  //      And("user's details should not be fetched from User Details")
  //      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))
  //
  //      And("sa returns should be fetched from Sa micro service")
  //      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
  //    }
  //
  //    scenario("a user logged in through GG and has no sa and no business enrolment with individual affinity group and inactive enrolments should be redirected to BTA") {
  //
  //      Given("a user logged in through Government Gateway")
  //      createStubs(TaxAccountUser(affinityGroup = AffinityGroupValue.INDIVIDUAL))
  //
  //      And("the user has an inactive enrolment and individual affinity group")
  //      stubInactiveEnrolments()
  //      stubUserDetails(affinityGroup = Some(AffinityGroupValue.INDIVIDUAL))
  //
  //      createStubs(BtaHomeStubPage)
  //
  //      When("the user hits the router")
  //      go(RouterRootPath)
  //
  //      Then("the user should be routed to BTA Home Page")
  //      on(BtaHomePage)
  //
  //      And("the authority object should be fetched once for AuthenticatedBy")
  //      verifyAuthorityObjectIsFetchedByCredId()
  //
  //      And("user's enrolments should be fetched from Auth")
  //      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))
  //
  //      And("user's details should be fetched from User Details")
  //      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))
  //
  //      And("Sa micro service should not be invoked")
  //      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
  //    }
  //
  //    scenario("a user logged in through GG and has no sa and no business enrolment with individual affinity group and no inactive enrolments should be redirected to PTA") {
  //
  //      Given("a user logged in through Government Gateway")
  //      createStubs(TaxAccountUser(affinityGroup = AffinityGroupValue.INDIVIDUAL))
  //
  //      And("the user has no inactive enrolments and individual affinity group")
  //      stubNoEnrolments()
  //      stubUserDetails(affinityGroup = Some(AffinityGroupValue.INDIVIDUAL))
  //
  //      createStubs(PtaHomeStubPage)
  //
  //      When("the user hits the router")
  //      go(RouterRootPath)
  //
  //      Then("the user should be routed to PTA Home Page")
  //      on(PtaHomePage)
  //
  //      And("the authority object should be fetched once for AuthenticatedBy")
  //      verifyAuthorityObjectIsFetchedByCredId()
  //
  //      And("user's enrolments should be fetched from Auth")
  //      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))
  //
  //      And("user's details should be fetched from User Details")
  //      verify(getRequestedFor(urlEqualTo("/user-details-uri")))
  //
  //      And("Sa micro service should not be invoked")
  //      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
  //    }
  //
  //    scenario("a user logged in through GG and has no sa and no business enrolment and no inactive enrolments and affinity group not available should be redirected to BTA") {
  //
  //      Given("a user logged in through Government Gateway")
  //      createStubs(TaxAccountUser(affinityGroup = AffinityGroupValue.INDIVIDUAL))
  //
  //      And("the user has no inactive enrolments and affinity group is not available")
  //      stubNoEnrolments()
  //      stubUserDetailsToReturn500()
  //
  //      createStubs(BtaHomeStubPage)
  //
  //      When("the user hits the router")
  //      go(RouterRootPath)
  //
  //      Then("the user should be routed to PTA Home Page")
  //      on(BtaHomePage)
  //
  //      And("the authority object should be fetched once for AuthenticatedBy")
  //      verifyAuthorityObjectIsFetchedByCredId()
  //
  //      And("user's enrolments should be fetched from Auth")
  //      verify(getRequestedFor(urlEqualTo("/auth/enrolments-uri")))
  //
  //      And("user's details should be fetched from User Details")
  //      verify(getRequestedFor(urlEqualTo("/user-details-uri")))
  //
  //      And("Sa micro service should not be invoked")
  //      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
  //    }

  private def verifyAuthorityObjectIsFetchedByCredId(credId: String) = {
    verify(getRequestedFor(urlEqualTo(s"/auth/gg/$credId")))
    verify(getRequestedFor(urlEqualTo("/auth/ids-uri")))
  }
}