/*
 * Copyright 2021 HM Revenue & Customs
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

package services

import cats.data.WriterT
import config.FrontendAppConfig
import engine.{AuditInfo, ThrottlingInfo}
import model.Locations._
import model._
import org.joda.time.{DateTime, DateTimeUtils, DateTimeZone}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import support.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ThrottlingServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterAll with ScalaFutures {

  private val longLiveDocumentExpirationTime: String = "3016-02-15T00:00"
  private val shortLiveDocumentExpirationSeconds: Int = 1

  val expirationDate: DateTime = DateTime.parse(longLiveDocumentExpirationTime)

  val fixedDateTime: DateTime = DateTime.now().withZone(DateTimeZone.UTC)

  override protected def beforeAll(): Unit = {
    DateTimeUtils.setCurrentMillisFixed(fixedDateTime.getMillis)
  }

  override protected def afterAll(): Unit = {
    DateTimeUtils.setCurrentMillisSystem()
  }

  val userIdentifier: String = "f3c48669-ecd2-41a4-ac51-aa6b9898b462"

  def createConfiguration(enabled: Boolean = true, locationName: String = "default-location-name", percentageToBeThrottled: Int = 0, fallbackLocation: String = "default-fallback-location", stickyRoutingEnabled: Boolean = false): Map[String, Any] = {
    Map[String, Any](
      "throttling.enabled" -> enabled,
      s"throttling.locations.$locationName.percentageToBeThrottled" -> percentageToBeThrottled,
      s"throttling.locations.$locationName.fallback" -> fallbackLocation,
      "sticky-routing.enabled" -> stickyRoutingEnabled,
      "sticky-routing.long-live-cache-expiration-time" -> longLiveDocumentExpirationTime,
      "sticky-routing.short-live-cache-duration" -> shortLiveDocumentExpirationSeconds
    )
  }

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(fakeRequest.headers)

  "ThrottlingService" should {

    "not throttle if throttling disabled and sticky routing disabled" in {
      val fakeApplication = GuiceApplicationBuilder().configure(createConfiguration(enabled = false)).build()

      running(fakeApplication) {
        //given
        val initialLocation = BusinessTaxAccount

        //and
        val mockRuleContext = mock[RuleContext]
        when(mockRuleContext.internalUserIdentifier(any())).thenReturn(Future.successful(Some(userIdentifier)))

        //and
        val initialAuditInfo = AuditInfo.Empty
        val ruleEngineResult = WriterT(Future.successful((initialAuditInfo, initialLocation)))

        //when
        val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]

        val throttler = new ThrottlingService(mockAppConfig)
        val (auditInfo, location) = throttler.throttle(ruleEngineResult, mockRuleContext).run.futureValue

        //then
        location shouldBe initialLocation

        //and
        auditInfo.throttlingInfo shouldBe Some(ThrottlingInfo(None, throttled = false, initialLocation, throttlingEnabled = false))
      }
    }

    "not throttle if throttling enabled and decides no" in {
      val fakeApplication = GuiceApplicationBuilder().configure(createConfiguration()).build()
      running(fakeApplication) {
        //given
        val initialLocation = BusinessTaxAccount
        //implicit val mockRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

        //and
        val mockRuleContext = mock[RuleContext]
        when(mockRuleContext.internalUserIdentifier(any())).thenReturn(Future.successful(Some(userIdentifier)))

        //and
        val initialAuditInfo = AuditInfo.Empty
        val ruleEngineResult = WriterT(Future.successful((initialAuditInfo, initialLocation)))

        //when
        val appConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]

        val throttler = new ThrottlingService(appConfig)
        val (auditInfo, location) = throttler.throttle(ruleEngineResult, mockRuleContext).run.futureValue

        //then
        location shouldBe initialLocation

        //and
        val expectedThrottlingInfo = ThrottlingInfo(Some(0), throttled = false, initialLocation, throttlingEnabled = true)
        auditInfo.throttlingInfo shouldBe Some(expectedThrottlingInfo)
      }
    }
  }

  it should {

    def evaluateUsingPlay[T](block: => T): T = {
      running(GuiceApplicationBuilder().build()) {
        block
      }
    }

    val initialLocation = evaluateUsingPlay(BusinessTaxAccount)

    val scenarios = evaluateUsingPlay {
      Table(
        ("scenario", "percentageBeToThrottled", "fallbackLocation", "expectedLocation", "throttled"),
        ("not throttle to fallback when user modulus is above threshold", 50, PersonalTaxAccount.name, BusinessTaxAccount, false),
        ("throttle to fallback when user modulus is equal to threshold", 51, PersonalTaxAccount.name, PersonalTaxAccount, true),
        ("throttle (but set throttle=false in audit event) when throttleLocation and initialLocation are the same", 51, initialLocation.name, initialLocation, false)
      )
    }

    forAll(scenarios) { (scenario: String, percentageBeToThrottled: Int, fallbackLocation, expectedLocation: Location, throttled: Boolean) =>
      scenario in {
        val fakeApplication = GuiceApplicationBuilder()
          .configure(
            createConfiguration(locationName = initialLocation.name,
              percentageToBeThrottled = percentageBeToThrottled,
              fallbackLocation = fallbackLocation)
          ).build()

        running(fakeApplication) {
          //given
          val mockRuleContext = mock[RuleContext]
          when(mockRuleContext.internalUserIdentifier(any())).thenReturn(Future.successful(Some(userIdentifier)))

          //and
          val initialAuditInfo = AuditInfo.Empty
          val ruleEngineResult = WriterT(Future.successful((initialAuditInfo, initialLocation)))

          //when
          val appConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]

          val throttler = new ThrottlingService(appConfig)
          val (auditInfo, returnedLocation) = throttler.throttle(ruleEngineResult, mockRuleContext).run.futureValue

          //then
          returnedLocation shouldBe expectedLocation

          //and
          auditInfo.throttlingInfo shouldBe Some(ThrottlingInfo(Some(percentageBeToThrottled), throttled, initialLocation, throttler.throttlingEnabled))
        }
      }
    }
  }

}
