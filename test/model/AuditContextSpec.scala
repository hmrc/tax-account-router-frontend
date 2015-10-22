/*
 * Copyright 2015 HM Revenue & Customs
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
import model.AuditEventType._
import model.Location.LocationType
import org.joda.time.{DateTime, DateTimeUtils, DateTimeZone}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.domain._
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
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

      AuditContext.defaultReasons shouldBe Map(
        "is-a-verify-user" -> "-",
        "is-a-government-gateway-user" -> "-",
        "has-never-seen-welcome-page-before" -> "-",
        "has-print-preferences-already-set" -> "-",
        "has-business-enrolments" -> "-",
        "has-previous-returns" -> "-",
        "is-in-a-partnership" -> "-",
        "is-self-employed" -> "-",
        "has-self-assessment-enrolments" -> "-"
      )
    }
  }

  "audit context" should {

    "return an extended audit event" in {
      val auditContext: TAuditContext = AuditContext()

      auditContext.setValue(IS_A_VERIFY_USER, true)
      auditContext.setValue(IS_A_GOVERNMENT_GATEWAY_USER, true)
      auditContext.setValue(HAS_NEVER_SEEN_WELCOME_PAGE_BEFORE, true)
      auditContext.setValue(HAS_PRINT_PREFERENCES_ALREADY_SET, true)
      auditContext.setValue(HAS_BUSINESS_ENROLMENTS, true)
      auditContext.setValue(HAS_PREVIOUS_RETURNS, true)
      auditContext.setValue(IS_IN_A_PARTNERSHIP, true)
      auditContext.setValue(IS_SELF_EMPLOYED, true)
      auditContext.setValue(HAS_SA_ENROLMENTS, true)

      val path = "/some/path"
      val destination = "/some/destination"
      val authId: String = "authId"
      implicit val authContext: AuthContext = AuthContext(LoggedInUser(authId, None, None, None, LevelOfAssurance.LOA_1), Principal(None, Accounts()), None)
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(method = "GET", uri = path, headers = FakeHeaders(), remoteAddress = "127.0.0.1", body = null)
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val reasonsMap = Map(
        "is-a-verify-user" -> "true",
        "is-a-government-gateway-user" -> "true",
        "has-never-seen-welcome-page-before" -> "true",
        "has-print-preferences-already-set" -> "true",
        "has-business-enrolments" -> "true",
        "has-previous-returns" -> "true",
        "is-in-a-partnership" -> "true",
        "is-self-employed" -> "true",
        "has-self-assessment-enrolments" -> "true"
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
      auditEvent.tags("transactionName") shouldBe "transaction-name"

      auditEvent.detail shouldBe Json.obj(
        "authId" -> authId,
        "destination" -> destination,
        "reasons" -> reasonsMap,
        "throttling" -> throttlingMap
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
        val destination = "/some/destination"
        val authId: String = "authId"

        val accounts: Accounts = Accounts(
          epaye = epaye,
          sa = sa,
          ct = ct,
          vat = vat
        )

        implicit val authContext = AuthContext(LoggedInUser(authId, None, None, None, LevelOfAssurance.LOA_1), Principal(None, accounts), None)
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

    val destination = evaluateUsingPlay(Location.PersonalTaxAccount)

    val scenarios = Table(
      ("scenario", "throttlingPercentage", "throttled", "throttlingPercentageString", "initialDestination", "enabled"),
      ("without percentage configured", None, "-", false, destination, false),
      ("with percentage configured", Option(1f), "1.0", true, destination, true)
    )

    forAll(scenarios) { (scenario: String, throttlingPercentage: Option[Float], throttlingPercentageString: String, throttled: Boolean, initialDestination: LocationType, enabled: Boolean) =>

      s"with the throttling audit context - scenario: $scenario" in {
        //given
        val auditContext: TAuditContext = AuditContext()

        //and
        implicit val authContext = AuthContext(LoggedInUser("", None, None, None, LevelOfAssurance.LOA_1), Principal(None, Accounts()), None)
        implicit val fakeRequest = FakeRequest(method = "GET", uri = "", headers = FakeHeaders(), remoteAddress = "127.0.0.1", body = null)
        implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

        //and
        val throttlingAuditContext = ThrottlingAuditContext(throttlingPercentage, throttled, destination, enabled)
        auditContext.setValue(throttlingAuditContext)

        //when
        val futureDataEvent: Future[ExtendedDataEvent] = auditContext.toAuditEvent(destination.url)
        val auditEvent = await(futureDataEvent)

        //then
        (auditEvent.detail \ "throttling" \ "enabled").as[String] shouldBe enabled.toString
        (auditEvent.detail \ "throttling" \ "percentage").as[String] shouldBe throttlingPercentageString
        (auditEvent.detail \ "throttling" \ "throttled").as[String] shouldBe throttled.toString
        (auditEvent.detail \ "throttling" \ "destination-before-throttling").as[String] shouldBe initialDestination.url
      }
    }
  }
}