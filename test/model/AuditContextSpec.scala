/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package model

import helpers.SpecHelpers
import model.RoutingReason._
import org.joda.time.{DateTime, DateTimeUtils, DateTimeZone}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.domain._
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuditContextSpec extends UnitSpec with WithFakeApplication with MockitoSugar with SpecHelpers {

  val fixedDateTime = DateTime.now(DateTimeZone.UTC)

  override def beforeAll(): Unit = {
    super.beforeAll()
    DateTimeUtils.setCurrentMillisFixed(fixedDateTime.getMillis)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    DateTimeUtils.setCurrentMillisSystem()
  }

  "default reasons" should {

    "be the expected ones" in {

      AuditContext.defaultRoutingReasons shouldBe Map(
        "is-a-verify-user" -> "-",
        "is-a-government-gateway-user" -> "-",
        "gg-enrolments-available" -> "-",
        "has-business-enrolments" -> "-",
        "sa-return-available" -> "-",
        "has-previous-returns" -> "-",
        "is-in-a-partnership" -> "-",
        "is-self-employed" -> "-",
        "has-self-assessment-enrolments" -> "-",
        "has-nino" -> "-"
      )
    }
  }

  "audit context" should {

    "return an extended audit event" in {
      val auditContext: TAuditContext = AuditContext()

      auditContext.setRoutingReason(IS_A_VERIFY_USER, result = true)
      auditContext.setRoutingReason(IS_A_GOVERNMENT_GATEWAY_USER, result = true)
      auditContext.setRoutingReason(GG_ENROLMENTS_AVAILABLE, result = true)
      auditContext.setRoutingReason(HAS_BUSINESS_ENROLMENTS, result = true)
      auditContext.setRoutingReason(SA_RETURN_AVAILABLE, result = true)
      auditContext.setRoutingReason(HAS_PREVIOUS_RETURNS, result = true)
      auditContext.setRoutingReason(IS_IN_A_PARTNERSHIP, result = true)
      auditContext.setRoutingReason(IS_SELF_EMPLOYED, result = true)
      auditContext.setRoutingReason(HAS_SA_ENROLMENTS, result = true)
      auditContext.setRoutingReason(HAS_NINO, result = true)

      auditContext.ruleApplied = "rule-name"

      val path = "/some/path"
      val destination = Location("/some/destination", "location-name")
      val authId: String = "authId"
      implicit val authContext: AuthContext = AuthContext(LoggedInUser(authId, None, None, None, CredentialStrength.None, ConfidenceLevel.L0), Principal(None, Accounts()), None)
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(method = "GET", uri = path, headers = FakeHeaders(), remoteAddress = "127.0.0.1", body = null)
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val reasonsMap = Map(
        "is-a-verify-user" -> "true",
        "is-a-government-gateway-user" -> "true",
        "gg-enrolments-available" -> "true",
        "has-business-enrolments" -> "true",
        "has-self-assessment-enrolments" -> "true",
        "sa-return-available" -> "true",
        "has-previous-returns" -> "true",
        "is-in-a-partnership" -> "true",
        "is-self-employed" -> "true",
        "has-self-assessment-enrolments" -> "true",
        "has-nino" -> "true"
      )

      val throttlingMap: Map[String, String] = Map()

      val futureAuditEvent: Future[ExtendedDataEvent] = auditContext.toAuditEvent(destination)
      val auditEvent = await(futureAuditEvent)

      auditEvent.auditSource shouldBe "tax-account-router-frontend"
      auditEvent.auditType shouldBe "Routing"
      auditEvent.eventId should fullyMatch regex """^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$""".r

      auditEvent.tags.contains("clientIP")
      auditEvent.tags("path") shouldBe path
      auditEvent.tags.contains("X-Session-ID")
      auditEvent.tags.contains("X-Request-ID")
      auditEvent.tags.contains("clientPort")
      auditEvent.tags("transactionName") shouldBe "unknown transaction"

      auditEvent.detail shouldBe Json.obj(
        "authId" -> authId,
        "destination" -> destination.url,
        "reasons" -> reasonsMap,
        "throttling" -> throttlingMap,
        "ruleApplied" -> "rule-name"
      )

      auditEvent.generatedAt shouldBe fixedDateTime
    }

  }

  it should {
    val scenarios = Table(
      ("scenario", "epaye", "sa", "ct", "vat"),
      ("paye defined", Some(EpayeAccount("", EmpRef("taxOfficeNumber", "taxOfficeReference"))), None, None, None),
      ("sa defined", None, Some(SaAccount("", SaUtr("saUtr"))), None, None),
      ("ct defined", None, None, Some(CtAccount("", CtUtr("ctUtr"))), None),
      ("vat defined", None, None, None, Some(VatAccount("", Vrn("vrn"))))
    )

    forAll(scenarios) { (scenario: String, epaye: Option[EpayeAccount], sa: Option[SaAccount], ct: Option[CtAccount], vat: Option[VatAccount]) =>

      s"add to the extended event optional fields - scenario: $scenario" in {

        val auditContext: TAuditContext = AuditContext()

        val path = "/some/path"
        val destination = Location("/some/destination", "location-name")
        val authId: String = "authId"

        val accounts: Accounts = Accounts(
          epaye = epaye,
          sa = sa,
          ct = ct,
          vat = vat
        )

        implicit val authContext = AuthContext(LoggedInUser(authId, None, None, None, CredentialStrength.None, ConfidenceLevel.L0), Principal(None, accounts), None)
        implicit val fakeRequest = FakeRequest(method = "GET", uri = path, headers = FakeHeaders(), remoteAddress = "127.0.0.1", body = null)
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        val futureDataEvent: Future[ExtendedDataEvent] = auditContext.toAuditEvent(destination)
        val auditEvent = await(futureDataEvent)

        (auditEvent.detail \ "empRef").asOpt[String] shouldBe epaye.fold[Option[String]](None) { paye => Some(paye.empRef.value) }
        (auditEvent.detail \ "saUtr").asOpt[String] shouldBe sa.fold[Option[String]](None) { sa => Some(sa.utr.value) }
        (auditEvent.detail \ "ctUtr").asOpt[String] shouldBe ct.fold[Option[String]](None) { ct => Some(ct.utr.value) }
        (auditEvent.detail \ "vrn").asOpt[String] shouldBe vat.fold[Option[String]](None) { vat => Some(vat.vrn.value) }
      }
    }
  }

  it should {

    val destination = evaluateUsingPlay(Locations.PersonalTaxAccount)

    val scenarios = Table(
      ("scenario", "throttlingPercentage", "throttled", "throttlingPercentageString", "initialDestination", "enabled", "stickyRoutingApplied"),
      ("without percentage configured", None, "-", false, destination, false, false),
      ("with percentage configured", Option(100), "100", true, destination, true, true)
    )

    forAll(scenarios) { (scenario: String, throttlingPercentage: Option[Int], throttlingPercentageString: String, throttled: Boolean, initialDestination: Location, enabled: Boolean, stickyRoutingApplied: Boolean) =>

      s"add to the extended event throttling-related fields - scenario: $scenario" in {
        //given
        val auditContext: TAuditContext = AuditContext()

        //and
        implicit val authContext = AuthContext(LoggedInUser("", None, None, None, CredentialStrength.None, ConfidenceLevel.L0), Principal(None, Accounts()), None)
        implicit val fakeRequest = FakeRequest(method = "GET", uri = "", headers = FakeHeaders(), remoteAddress = "127.0.0.1", body = null)
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        //and
        val throttlingAuditContext = ThrottlingAuditContext(throttlingPercentage, throttled, destination, enabled, stickyRoutingApplied = stickyRoutingApplied)
        auditContext.setThrottlingDetails(throttlingAuditContext)

        //when
        val futureDataEvent: Future[ExtendedDataEvent] = auditContext.toAuditEvent(destination)
        val auditEvent = await(futureDataEvent)

        //then
        (auditEvent.detail \ "throttling" \ "enabled").as[String] shouldBe enabled.toString
        (auditEvent.detail \ "throttling" \ "percentage").as[String] shouldBe throttlingPercentageString
        (auditEvent.detail \ "throttling" \ "throttled").as[String] shouldBe throttled.toString
        (auditEvent.detail \ "throttling" \ "destination-url-before-throttling").as[String] shouldBe initialDestination.url
        (auditEvent.detail \ "throttling" \ "destination-name-before-throttling").as[String] shouldBe initialDestination.name
        (auditEvent.detail \ "throttling" \ "sticky-routing-applied").as[String] shouldBe stickyRoutingApplied.toString
      }
    }
  }
}
