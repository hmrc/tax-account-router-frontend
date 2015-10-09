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

import model.AuditEventType._
import org.joda.time.{DateTime, DateTimeUtils, DateTimeZone}
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuditContextSpec extends UnitSpec with WithFakeApplication {

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
        "has-already-seen-welcome-page" -> "-",
        "has-print-preferences-already-set" -> "-",
        "has-business-enrolments" -> "-",
        "has-previous-returns" -> "-",
        "is-in-a-partnership" -> "-",
        "is-self-employed" -> "-"
      )
    }
  }

  "audit context" should {

    "return an extended audit event" in {
      val auditContext: TAuditContext = AuditContext()

      val hasSeenWelcomePage: Future[Boolean] = auditContext.setValue(HAS_ALREADY_SEEN_WELCOME_PAGE, Future(true))
      val hasPrintPreferencesSet: Future[Boolean] = auditContext.setValue(HAS_PRINT_PREFERENCES_ALREADY_SET, Future(true))
      val hasPreviousReturns: Future[Boolean] = auditContext.setValue(HAS_PREVIOUS_RETURNS, Future(true))
      val hasBusinessEnrolments: Future[Boolean] = auditContext.setValue(HAS_BUSINESS_ENROLMENTS, Future(true))
      val isInAPartnership: Future[Boolean] = auditContext.setValue(IS_IN_A_PARTNERSHIP, Future(true))
      val isSelfEmployed: Future[Boolean] = auditContext.setValue(IS_SELF_EMPLOYED, Future(true))

      val path = "/some/path"
      val destination = "/some/destination"
      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(method = "GET", uri = path, headers = FakeHeaders(), remoteAddress = "127.0.0.1", body = null)
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      val reasonsMap = Map(
        "has-already-seen-welcome-page" -> "true",
        "has-print-preferences-already-set" -> "true",
        "has-business-enrolments" -> "true",
        "has-previous-returns" -> "true",
        "is-in-a-partnership" -> "true",
        "is-self-employed" -> "true"
      )

      val result = for {
        welcomePageSeen <- hasSeenWelcomePage
        printPreferencesSet <- hasPrintPreferencesSet
        previousReturns <- hasPreviousReturns
        businessEnrolments <- hasBusinessEnrolments
        partnership <- isInAPartnership
        selfEmployed <- isSelfEmployed
      } yield selfEmployed

      await(result)

      val auditEvent: ExtendedDataEvent = auditContext.toAuditEvent(destination)

      auditEvent.auditSource shouldBe "tax-account-router-frontend"
      auditEvent.auditType shouldBe "Routing"
      auditEvent.eventId should fullyMatch regex """^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$""".r
      auditEvent.tags.contains("clientIP")
      auditEvent.tags("path") shouldBe path
      auditEvent.tags.contains("X-Session-ID")
      auditEvent.tags.contains("X-Request-ID")
      auditEvent.tags.contains("clientPort")
      auditEvent.tags("transactionName") shouldBe "transaction-name"
      auditEvent.detail shouldBe Json.obj("destination" -> destination, "reasons" -> reasonsMap)
      auditEvent.generatedAt shouldBe fixedDateTime
    }
  }
}
