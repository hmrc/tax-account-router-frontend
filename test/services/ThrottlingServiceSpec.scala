/*
 * Copyright 2017 HM Revenue & Customs
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
import connector.InternalUserIdentifier
import engine.{AuditInfo, ThrottlingInfo}
import helpers.SpecHelpers
import model.Locations._
import model._
import org.joda.time.{DateTime, DateTimeUtils, DateTimeZone}
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.Configuration
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ThrottlingServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterAll with SpecHelpers with ScalaFutures {

  private val longLiveDocumentExpirationTime: String = "3016-02-15T00:00"
  private val shortLiveDocumentExpirationSeconds: Int = 1

  val expirationDate: DateTime = DateTime.parse(longLiveDocumentExpirationTime)

  val fixedDateTime = DateTime.now().withZone(DateTimeZone.UTC)

  override protected def beforeAll(): Unit = {
    DateTimeUtils.setCurrentMillisFixed(fixedDateTime.getMillis)
  }

  override protected def afterAll(): Unit = {
    DateTimeUtils.setCurrentMillisSystem()
  }

  private val discriminatorWithThrottlerModulus50 = "f3c48669-ecd2-41a4-ac51-aa6b9898b462"
  val userIdentifier = InternalUserIdentifier(discriminatorWithThrottlerModulus50)

  def createConfiguration(enabled: Boolean = true, locationName: String = "default-location-name", percentageToBeThrottled: Int = 0, fallbackLocation: String = "default-fallback-location", stickyRoutingEnabled: Boolean = false) = {
    Map[String, Any](
      "throttling.enabled" -> enabled,
      s"throttling.locations.$locationName.percentageToBeThrottled" -> percentageToBeThrottled,
      s"throttling.locations.$locationName.fallback" -> fallbackLocation,
      "sticky-routing.enabled" -> stickyRoutingEnabled,
      "sticky-routing.long-live-cache-expiration-time" -> longLiveDocumentExpirationTime,
      "sticky-routing.short-live-cache-duration" -> shortLiveDocumentExpirationSeconds
    )
  }

  "ThrottlingService" should {

    "not throttle if throttling disabled and sticky routing disabled" in {
      val fakeApplication = FakeApplication(additionalConfiguration = createConfiguration(enabled = false))
      running(fakeApplication) {
        //given
        val initialLocation = BusinessTaxAccount
        implicit val mockRequest = FakeRequest()

        //and
        val mockRuleContext = mock[RuleContext]
        when(mockRuleContext.internalUserIdentifier).thenReturn(Future.successful(Some(userIdentifier)))

        //and
        val initialAuditInfo = AuditInfo.Empty
        val ruleEngineResult = WriterT(Future.successful((initialAuditInfo, initialLocation)))

        val locationConfigurationFactory = new LocationConfigurationFactory {
          override val configuration: Configuration = fakeApplication.configuration
        }

        //when
        val throttler = new ThrottlingServiceTest(locationConfigurationFactory)
        val (auditInfo, location) = throttler.throttle(ruleEngineResult, mockRuleContext).run.futureValue

        //then
        location shouldBe initialLocation

        //and
        auditInfo.throttlingInfo shouldBe Some(ThrottlingInfo(None, false, initialLocation, false))
      }
    }

    "not throttle if throttling enabled and decides no" in {
      val fakeApplication = FakeApplication(additionalConfiguration = createConfiguration())
      running(fakeApplication) {
        //given
        val initialLocation = BusinessTaxAccount
        implicit val mockRequest = FakeRequest()

        //and
        val mockRuleContext = mock[RuleContext]
        when(mockRuleContext.internalUserIdentifier).thenReturn(Future.successful(Some(userIdentifier)))

        //and
        val initialAuditInfo = AuditInfo.Empty
        val ruleEngineResult = WriterT(Future.successful((initialAuditInfo, initialLocation)))

        val locationConfigurationFactory = new LocationConfigurationFactory {
          override val configuration: Configuration = fakeApplication.configuration
        }

        //when
        val throttler = new ThrottlingServiceTest(locationConfigurationFactory)
        val (auditInfo, location) = throttler.throttle(ruleEngineResult, mockRuleContext).run.futureValue

        //then
        location shouldBe initialLocation

        //and
        auditInfo.throttlingInfo shouldBe None // FIXME there should be some ThrottleInfo
      }
    }
  }

  it should {
    val initialLocation = evaluateUsingPlay(BusinessTaxAccount)

    val scenarios = evaluateUsingPlay {
      Table(
        ("scenario", "percentageBeToThrottled", "randomNumber", "expectedLocation", "throttled"),
        ("Should throttle to fallback when random number is less than percentage", 50, 10, PersonalTaxAccount, true),
        ("Should throttle to fallback when random number is equal than percentage", 50, 49, PersonalTaxAccount, true),
        ("Should not throttle to fallback when random number is equal than percentage", 50, 70, initialLocation, false)
      )
    }

    forAll(scenarios) { (scenario: String, percentageBeToThrottled: Int, randomNumber: Int, expectedLocation: Location, throttled: Boolean) =>
      s"return the correct location and update audit context - scenario: $scenario" in {
        val fakeApplication = FakeApplication(additionalConfiguration = createConfiguration(
          locationName = initialLocation.name,
          percentageToBeThrottled = percentageBeToThrottled,
          fallbackLocation = expectedLocation.name
        ))

        running(fakeApplication) {
          //given
          val mockRuleContext = mock[RuleContext]
          when(mockRuleContext.internalUserIdentifier).thenReturn(Future.successful(Some(userIdentifier)))

          //and
          val initialAuditInfo = AuditInfo.Empty
          val ruleEngineResult = WriterT(Future.successful((initialAuditInfo, initialLocation)))

          val locationConfigurationFactory = new LocationConfigurationFactory {
            override val configuration: Configuration = fakeApplication.configuration
          }

          //when
          val throttlingServiceTest = new ThrottlingServiceTest(locationConfigurationFactory)
          val (auditInfo, returnedLocation) = throttlingServiceTest.throttle(ruleEngineResult, mockRuleContext).run.futureValue

          //then
          returnedLocation shouldBe expectedLocation

          //and
          auditInfo.throttlingInfo shouldBe Some(ThrottlingInfo(Some(percentageBeToThrottled), throttled, initialLocation, throttlingServiceTest.throttlingEnabled))
        }
      }
    }
  }
}

class ThrottlingServiceTest(override val locationConfigurationFactory: LocationConfigurationFactory) extends ThrottlingService
