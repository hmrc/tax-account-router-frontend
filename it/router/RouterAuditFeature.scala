package router

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{RequestPatternBuilder, WireMock}
import connector.AffinityGroupValue
import model.AuditContext
import model.RoutingReason._
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeApplication
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, CredentialStrength, PayeAccount, SaAccount}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => mutableMap}

class RouterAuditFeature extends StubbedFeatureSpec with CommonStubs {

  val additionalConfiguration = Map[String, Any](
    "business-enrolments" -> "enr1,enr2",
    "self-assessment-enrolments" -> "enr3,enr4",
    "ws.timeout.request" -> 10000,
    "ws.timeout.connection" -> 6000,
    "two-step-verification.enabled" -> true,
    "logger.application" -> "ERROR",
    "logger.connector" -> "ERROR"
  )

  override lazy val app = FakeApplication(additionalConfiguration = config ++ additionalConfiguration)

  feature("Router audit feature") {

    scenario("a user logged in through Verify should be redirected and an audit event should be raised") {

      Given("a user logged in through Verify")
      createStubs(TaxAccountUser(tokenPresent = false))

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons += (
        IS_A_VERIFY_USER.key -> "true"
        ))
      val expectedTransactionName = "sent to personal tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "pta-home-page-for-verify-user")
    }

    scenario("a user logged in through GG with any business account will be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      createStubs(TaxAccountUser())

      And("the user has business related enrolments")
      stubBusinessEnrolments()

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "true",
        HAS_STRONG_CREDENTIALS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "false",
        HAS_ONLY_ONE_ENROLMENT.key -> "true"
        ))
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-business-enrolments")
    }

    scenario("a user logged in through GG with self assessment enrolments and no previous returns should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "false",
        HAS_REGISTERED_FOR_2SV.key -> "true",
        HAS_STRONG_CREDENTIALS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        HAS_ONLY_ONE_ENROLMENT.key -> "true"
        ))
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-no-previous-return")
    }

    scenario("a user logged in through GG and has no sa and no business enrolment should have no calls to sa in the audit record") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has no inactive enrolments")
      stubNoEnrolments()

      And("the user has organisation affinity group")
      stubUserDetails(affinityGroup = AffinityGroupValue.ORGANISATION)

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "false",
        HAS_STRONG_CREDENTIALS.key -> "false",
        HAS_ONLY_ONE_ENROLMENT.key -> "false",
        HAS_INDIVIDUAL_AFFINITY_GROUP.key -> "false"
        ))

      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-passed-through")
    }

    scenario("a user logged in through GG with self assessment enrolments and in a partnership should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user is in a partnership")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("partnership"))

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        IS_A_VERIFY_USER.key -> "false",
        IS_IN_A_PARTNERSHIP.key -> "true",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        HAS_REGISTERED_FOR_2SV.key -> "true",
        HAS_STRONG_CREDENTIALS.key -> "false",
        HAS_ONLY_ONE_ENROLMENT.key -> "true"
        ))

      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-partnership-or-self-employment")
    }

    scenario("a user logged in through GG with self assessment enrolments and self employed should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user is self employed")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("self_employment"))

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        IS_IN_A_PARTNERSHIP.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        IS_SELF_EMPLOYED.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        HAS_REGISTERED_FOR_2SV.key -> "true",
        HAS_STRONG_CREDENTIALS.key -> "false",
        HAS_ONLY_ONE_ENROLMENT.key -> "true"
        ))

      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-partnership-or-self-employment")
    }

    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with no NINO should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))), paye = None)
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has previous returns and is not in a partnership and is not self employed and has no NINO")
      stubSaReturn(saUtr, previousReturns = true)

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      Then("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        IS_A_VERIFY_USER.key -> "false",
        IS_IN_A_PARTNERSHIP.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        IS_SELF_EMPLOYED.key -> "false",
        HAS_PREVIOUS_RETURNS.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        HAS_NINO.key -> "false",
        HAS_SA_UTR.key -> "-",
        HAS_REGISTERED_FOR_2SV.key -> "true",
        HAS_STRONG_CREDENTIALS.key -> "false",
        HAS_ONLY_ONE_ENROLMENT.key -> "true"
        ))
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-no-partnership-and-no-self-employment-and-no-nino")
    }

    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with NINO should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))), paye = Some(PayeAccount("link", Nino("CS100700A"))))
      createStubs(TaxAccountUser(accounts = accounts))

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has previous returns and is not in a partnership and is not self employed and has NINO")
      stubSaReturn(saUtr, previousReturns = true)

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      Then("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        SA_RETURN_AVAILABLE.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "true",
        IS_SELF_EMPLOYED.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        IS_IN_A_PARTNERSHIP.key -> "false",
        HAS_NINO.key -> "true"
        ))
      val expectedTransactionName = "sent to personal tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "pta-home-page-for-user-with-no-partnership-and-no-self-employment")
    }

    scenario("a BTA eligible user with SAUTR and not registered for 2SV should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      And("the user has previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "false",
        HAS_REGISTERED_FOR_2SV.key -> "false",
        HAS_STRONG_CREDENTIALS.key -> "false",
        HAS_ONLY_ONE_ENROLMENT.key -> "true"
        ))
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-no-previous-return")
    }

    scenario("a BTA eligible user with NINO and not registered for 2SV should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val accounts = Accounts(paye = Some(PayeAccount("link", Nino("CS100700A"))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "false",
        HAS_REGISTERED_FOR_2SV.key -> "false",
        HAS_STRONG_CREDENTIALS.key -> "false",
        HAS_ONLY_ONE_ENROLMENT.key -> "true"
        ))
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-no-previous-return")
    }

    scenario("a BTA eligible user with NINO, not registered for 2SV but already has strong credentials should be not redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val accounts = Accounts(paye = Some(PayeAccount("link", Nino("CS100700A"))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false, credentialStrength = CredentialStrength.Strong))

      And("the user has self assessment enrolments")
      stubSelfAssessmentEnrolments()

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "false",
        HAS_STRONG_CREDENTIALS.key -> "true"
        ))
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-no-previous-return")
    }

    scenario("a BTA eligible user with NINO and not registered for 2SV and more than one enrolment should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      val accounts = Accounts(paye = Some(PayeAccount("link", Nino("CS100700A"))))
      createStubs(TaxAccountUser(accounts = accounts, isRegisteredFor2SV = false))

      And("the user has self assessment enrolments")
      stubMoreThanOneSAEnrolment()

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "true",
        HAS_STRONG_CREDENTIALS.key -> "false",
        HAS_ONLY_ONE_ENROLMENT.key -> "false"
        ))
      val expectedTransactionName = "sent to business tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-business-enrolments")
    }

    scenario("a user logged in through GG and has no sa and no business enrolment with individual affinity group should have no calls to sa in the audit record and be redirected") {

      Given("a user logged in through Government Gateway")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(accounts = accounts, affinityGroup = AffinityGroupValue.INDIVIDUAL))

      And("the user has self assessment enrolments and individual affinity group")
      stubNoEnrolments()

      And("the user has individual affinity group")
      stubUserDetails(affinityGroup = AffinityGroupValue.INDIVIDUAL)

      val auditEventStub = stubAuditEvent()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(AuditContext.defaultRoutingReasons +=(
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "false",
        HAS_INDIVIDUAL_AFFINITY_GROUP.key -> "true",
        HAS_ANY_INACTIVE_ENROLMENT.key -> "false"
        ))

      val expectedTransactionName = "sent to personal tax account"
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "pta-home-page-individual")
    }
  }

  def toJson(map: mutableMap[String, String]) = Json.obj(map.map { case (k, v) => k -> Json.toJsFieldJsValueWrapper(v) }.toSeq: _*)

  def verifyAuditEvent(auditEventStub: RequestPatternBuilder, expectedReasons: JsValue, expectedTransactionName: String, ruleApplied: String): Unit = {
    val loggedRequests = WireMock.findAll(auditEventStub).asScala.toList
    val event = Json.parse(loggedRequests
      .filter(s => s.getBodyAsString.matches( """^.*"auditType"[\s]*\:[\s]*"Routing".*$""")).head.getBodyAsString)
    (event \ "tags" \ "transactionName").as[String] shouldBe expectedTransactionName
    (event \ "detail" \ "ruleApplied").as[String] shouldBe ruleApplied
    (event \ "detail" \ "reasons") shouldBe expectedReasons
  }
}
