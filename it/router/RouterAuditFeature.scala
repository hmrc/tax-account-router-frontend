package router

import com.github.tomakehurst.wiremock.client.WireMock._
import engine.RoutingReason._
import model.AffinityGroupValue
import support.page._
import support.stubs._

class RouterAuditFeature extends StubbedFeatureSpec with CommonStubs with AuditTools {

  feature("Router audit feature") {

    scenario("a user logged in through Verify should be redirected and an audit event should be raised") {

      Given("a user logged in through Verify")
      setVerifyUser()

      val auditEventStub = stubAuditEvent()
      stubPersonalAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (IS_A_VERIFY_USER.key -> "true"))
      val expectedTransactionName = "sent to personal tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/personal-account",
        destinationNameBeforeThrottling = "personal-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "pta-home-page-for-verify-user", expectedThrottlingDetails)
    }

    scenario("a user logged in through GG with any business account will be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has business related enrolments")
      stubRetrievalALLEnrolments(enrolmentKey = "enr1")

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "true"
        ))
      val expectedTransactionName = "sent to business tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/business-account",
        destinationNameBeforeThrottling = "business-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-business-enrolments", expectedThrottlingDetails)
    }

    scenario("a user logged in through GG with self assessment enrolments and no previous returns should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      And("the user has self assessment enrolments")
      setGGUser()
      stubRetrievalALLEnrolments()

      And("the user has no previous returns")
      stubRetrievalSAUTR()
      stubSaReturnWithNoPreviousReturns(saUtr)

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        SA_RETURN_AVAILABLE.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true"
        ))
      val expectedTransactionName = "sent to business tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/business-account",
        destinationNameBeforeThrottling = "business-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-no-previous-return", expectedThrottlingDetails)
    }

    scenario("when a user logged in through GG and has no sa and no business enrolment an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has no inactive enrolments")
      stubRetrievalALLEnrolments(hasEnrolments = false)

      And("the user has organisation affinity group")
      stubRetrievalAffinityGroup()

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_ANY_INACTIVE_ENROLMENT.key -> "false",
        AFFINITY_GROUP_AVAILABLE.key -> "true",
        HAS_INDIVIDUAL_AFFINITY_GROUP.key -> "false",
        HAS_SA_ENROLMENTS.key -> "false"
        ))

      val expectedTransactionName = "sent to business tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/business-account",
        destinationNameBeforeThrottling = "business-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-passed-through", expectedThrottlingDetails)
    }

    scenario("a user logged in through GG with self assessment enrolments and in a partnership should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")

      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      And("the user is in a partnership")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("partnership"))

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        IS_A_VERIFY_USER.key -> "false",
        IS_IN_A_PARTNERSHIP.key -> "true",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true"
        ))

      val expectedTransactionName = "sent to business tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/business-account",
        destinationNameBeforeThrottling = "business-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-partnership-or-self-employment", expectedThrottlingDetails)
    }

    scenario("a user logged in through GG with self assessment enrolments and self employed should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      And("the user is self employed")
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("self_employment"))

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        IS_IN_A_PARTNERSHIP.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        IS_SELF_EMPLOYED.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true"
        ))

      val expectedTransactionName = "sent to business tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/business-account",
        destinationNameBeforeThrottling = "business-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-partnership-or-self-employment", expectedThrottlingDetails)
    }

    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with no NINO should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      And("the user has previous returns and is not in a partnership and is not self employed and has no NINO")
      stubSaReturn(saUtr, previousReturns = true)
      stubRetrievalNINO(hasNino = false)

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      Then("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        IS_A_VERIFY_USER.key -> "false",
        IS_IN_A_PARTNERSHIP.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        IS_SELF_EMPLOYED.key -> "false",
        HAS_PREVIOUS_RETURNS.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        HAS_NINO.key -> "false"
        ))
      val expectedTransactionName = "sent to business tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/business-account",
        destinationNameBeforeThrottling = "business-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-no-partnership-and-no-self-employment-and-no-nino", expectedThrottlingDetails)
    }

    scenario("a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with NINO should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      And("the user has previous returns and is not in a partnership and is not self employed and has NINO")
      stubSaReturn(saUtr, previousReturns = true)
      stubRetrievalNINO()

      val auditEventStub = stubAuditEvent()
      stubPersonalAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      Then("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
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
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/personal-account",
        destinationNameBeforeThrottling = "personal-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "pta-home-page-for-user-with-no-partnership-and-no-self-employment", expectedThrottlingDetails)
    }

    scenario("a BTA eligible user with SAUTR and not registered for 2SV should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      And("the user has no previous returns")
      stubSaReturnWithNoPreviousReturns(saUtr)

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "false"
        ))
      val expectedTransactionName = "sent to business tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/business-account",
        destinationNameBeforeThrottling = "business-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-no-previous-return", expectedThrottlingDetails)
    }

    scenario("a BTA eligible user with NINO and not registered for 2SV should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "false"
        ))
      val expectedTransactionName = "sent to business tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/business-account",
        destinationNameBeforeThrottling = "business-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-no-previous-return", expectedThrottlingDetails)
    }

    scenario("a BTA eligible user with NINO, not registered for 2SV but already has strong credentials should be not redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has self assessment enrolments")
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_SA_ENROLMENTS.key -> "true",
        SA_RETURN_AVAILABLE.key -> "true",
        HAS_PREVIOUS_RETURNS.key -> "false"
        ))
      val expectedTransactionName = "sent to business tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/business-account",
        destinationNameBeforeThrottling = "business-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-no-previous-return", expectedThrottlingDetails)
    }

    scenario("a BTA eligible user with NINO and not registered for 2SV and more than one enrolment should be redirected and an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user with business  enrolments")
      stubRetrievalALLEnrolments("enr1")
      stubRetrievalSAUTR()

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "true"
        ))
      val expectedTransactionName = "sent to business tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/business-account",
        destinationNameBeforeThrottling = "business-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-for-user-with-business-enrolments", expectedThrottlingDetails)
    }

    scenario("when a user logged in through GG and has no sa and no business enrolment with individual affinity group an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has no self assessment enrolments and no business enrolment, but has individual affinity group")
      stubRetrievalALLEnrolments(hasEnrolments = false)

      And("the user has individual affinity group")
      stubRetrievalAffinityGroup(AffinityGroupValue.INDIVIDUAL)

      val auditEventStub = stubAuditEvent()
      stubPersonalAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_INDIVIDUAL_AFFINITY_GROUP.key -> "true",
        HAS_ANY_INACTIVE_ENROLMENT.key -> "false",
        AFFINITY_GROUP_AVAILABLE.key -> "true",
        HAS_SA_ENROLMENTS.key -> "false"
        ))

      val expectedTransactionName = "sent to personal tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/personal-account",
        destinationNameBeforeThrottling = "personal-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "pta-home-page-individual", expectedThrottlingDetails)
    }

    scenario("when a user logged in through GG and has no sa and no business enrolment and affinity group not available an audit event should be raised") {

      Given("a user logged in through Government Gateway")
      setGGUser()

      And("the user has no enrolments")
      stubRetrievalALLEnrolments(hasEnrolments = false)

      And("affinity group is not available")

      val auditEventStub = stubAuditEvent()
      stubBusinessAccount()

      When("the user hits the router")
      go(RouterRootPath)

      Then("an audit event should be sent")
      verify(postRequestedFor(urlMatching("^/write/audit.*$")))

      And("the audit event raised should be the expected one")
      val expectedReasons = toJson(emptyRoutingReasons + (
        IS_A_VERIFY_USER.key -> "false",
        IS_A_GOVERNMENT_GATEWAY_USER.key -> "true",
        GG_ENROLMENTS_AVAILABLE.key -> "true",
        HAS_BUSINESS_ENROLMENTS.key -> "false",
        HAS_ANY_INACTIVE_ENROLMENT.key -> "false",
        AFFINITY_GROUP_AVAILABLE.key -> "false",
        HAS_SA_ENROLMENTS.key -> "false"
        ))
      val expectedTransactionName = "sent to business tax account"
      val expectedThrottlingDetails = ThrottlingDetails(
        enabled = false,
        percentage = "-",
        throttled = false,
        destinationUrlBeforeThrottling = "/business-account",
        destinationNameBeforeThrottling = "business-tax-account"
      )
      verifyAuditEvent(auditEventStub, expectedReasons, expectedTransactionName, "bta-home-page-affinity-group-unavailable", expectedThrottlingDetails)
    }
  }
}


