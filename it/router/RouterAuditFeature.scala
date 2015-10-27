package router

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{RequestPatternBuilder, WireMock}
import model.AuditContext
import model.RoutingReason._
import play.api.libs.json.Json
import play.api.test.FakeApplication
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, SaAccount}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => mutableMap}

class RouterAuditFeature extends StubbedFeatureSpec with CommonStubs {

  val enrolmentConfiguration = Map[String, Any](
        "business-enrolments" -> List("enr1", "enr2"),
        "self-assessment-enrolments" -> List("enr3", "enr4")
      )

  override lazy val app = FakeApplication(additionalConfiguration = config ++ enrolmentConfiguration)

  override def beforeEach(): Unit = {
    super.beforeEach()
    stubSave4LaterWelcomePageSeen() // all the following scenarios are assuming the welcome page to be already seen
  }

  feature("Router audit feature") {

    scenario("a user logged in for the first time and that never visited Welcome page should be redirected and an audit event should be raised") {

      Given("a user logged in for the first time")
      createStubs(TaxAccountUser(firstTimeLoggedIn = true))

      And("user has never seen the Welcome page before")
      stubSave4LaterToBeEmpty()

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = AuditContext.defaultRoutingReasons +=(
        HAS_NEVER_SEEN_WELCOME_PAGE_BEFORE.key -> "true",
        LOGGED_IN_FOR_THE_FIRST_TIME.key -> "true"
        )
      val expectedTransactionName = "sent to welcome page"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName)
    }

    scenario("a user logged in through Verify should be redirected and an audit event should be raised") {

      Given("a user logged in through Verify")
      createStubs(TaxAccountUser(tokenPresent = false))

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "true",
        LOGGED_IN_FOR_THE_FIRST_TIME.key -> "false"
        )
      val expectedTransactionName = "sent to personal tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName)
    }

    scenario("a user logged in through GG with any business account will be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      createStubs(TaxAccountUser())

      And("the user has business related enrolments")
      stubProfileWithBusinessEnrolments()

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        LOGGED_IN_FOR_THE_FIRST_TIME.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "true"
        )
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName)
    }

    scenario("a user logged in through GG with self assessment enrolments and no previous returns should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the user has previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        LOGGED_IN_FOR_THE_FIRST_TIME.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "false",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true"
        )
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName)
    }

    scenario("a user logged in through GG with self assessment enrolments and in a partnership should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the user is in a partnership")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("partnership"))

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_IN_A_PARTNERSHIP.key -> "true",
        LOGGED_IN_FOR_THE_FIRST_TIME.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true"
        )
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName)
    }

    scenario("a user logged in through GG with self assessment enrolments and self employed should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the user is self employed")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("self_employment"))

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_IN_A_PARTNERSHIP.key -> "false",
        LOGGED_IN_FOR_THE_FIRST_TIME.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        IS_SELF_EMPLOYED.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true"
        )
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName)
    }

    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the user has previous returns and is not in a partnership and is not self employed")
      stubSaReturn(saUtr, previousReturns = true)

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      Then("the audit event raised should be the expected one")
      val expectedReasons = AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_IN_A_PARTNERSHIP.key -> "false",
        LOGGED_IN_FOR_THE_FIRST_TIME.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        IS_SELF_EMPLOYED.key -> "false",
        HAS_PREVIOUS_RETURNS.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true"
        )
      val expectedTransactionName = "sent to personal tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName)
    }
  }

  def verifyAuditEvent(auditEventStub: RequestPatternBuilder, expectedReasons: mutableMap[String, String], expectedTransactionName: String): Unit = {
    val loggedRequests = WireMock.findAll(auditEventStub).asScala.toList
    val event = Json.parse(loggedRequests.filter(_.getBodyAsString.matches( """^.*"auditType"[\s]*\:[\s]*"Routing".*$""")).head.getBodyAsString)
    (event \ "tags" \ "transactionName").as[String] shouldBe expectedTransactionName
    (event \ "detail" \ "reasons" \ "is-a-verify-user").as[String] shouldBe expectedReasons(IS_A_VERIFY_USER.key)
    (event \ "detail" \ "reasons" \ "has-print-preferences-already-set").as[String] shouldBe expectedReasons(HAS_PRINT_PREFERENCES_ALREADY_SET.key)
    (event \ "detail" \ "reasons" \ "has-self-assessment-enrolments").as[String] shouldBe expectedReasons(HAS_SA_ENROLMENTS.key)
    (event \ "detail" \ "reasons" \ "is-in-a-partnership").as[String] shouldBe expectedReasons(IS_IN_A_PARTNERSHIP.key)
    (event \ "detail" \ "reasons" \ "logged-in-for-the-first-time").as[String] shouldBe expectedReasons(LOGGED_IN_FOR_THE_FIRST_TIME.key)
    (event \ "detail" \ "reasons" \ "is-a-government-gateway-user").as[String] shouldBe expectedReasons(IS_A_GOVERNMENT_GATEWAY_USER.key)
    (event \ "detail" \ "reasons" \ "has-never-seen-welcome-page-before").as[String] shouldBe expectedReasons(HAS_NEVER_SEEN_WELCOME_PAGE_BEFORE.key)
    (event \ "detail" \ "reasons" \ "is-self-employed").as[String] shouldBe expectedReasons(IS_SELF_EMPLOYED.key)
    (event \ "detail" \ "reasons" \ "has-previous-returns").as[String] shouldBe expectedReasons(HAS_PREVIOUS_RETURNS.key)
    (event \ "detail" \ "reasons" \ "has-business-enrolments").as[String] shouldBe expectedReasons(HAS_BUSINESS_ENROLMENTS.key)
  }
}
