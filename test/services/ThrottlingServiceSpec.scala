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

package services

import helpers.SpecHelpers
import model.Location._
import model.{AuditContext, RoutingInfo, ThrottlingAuditContext}
import org.joda.time.{DateTime, DateTimeUtils}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeRequest}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import repositories.RoutingCacheRepository
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, ConfidenceLevel, SaAccount}
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class ThrottlingServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with SpecHelpers {

  private val longLiveCookieExpirationTime: String = "2016-02-15T00:00"

  def createConfiguration(enabled: Boolean = true, locationName: String = "default-location-name", percentageBeToThrottled: Int = 0, fallbackLocation: String = "default-fallback-location", stickyRoutingEnabled: Boolean = false) = {
    Map[String, Any](
      "throttling.enabled" -> enabled,
      s"throttling.locations.$locationName.percentageBeToThrottled" -> percentageBeToThrottled,
      s"throttling.locations.$locationName.fallback" -> fallbackLocation,
      "sticky-routing.enabled" -> stickyRoutingEnabled,
      "sticky-routing.long-live-cookie-expiration-time" -> longLiveCookieExpirationTime,
      "sticky-routing.short-live-cookie-duration" -> 1
    )
  }

  def getAuthContext(utr: String) =
    AuthContext(LoggedInUser("", None, None, None, ConfidenceLevel.L0), Principal(Some(""), Accounts(sa = Some(SaAccount("", SaUtr(utr))))), None)

  "ThrottlingService" should {

    "not throttle if throttling disabled and sticky routing disabled" in {
      running(FakeApplication(additionalConfiguration = createConfiguration(enabled = false))) {
        //given
        val initialLocation = mock[LocationType]
        implicit val mockRequest = FakeRequest()
        implicit val authContext = getAuthContext("")

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]

        //when
        val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(initialLocation, mock[AuditContext])

        //then
        await(returnedLocation) shouldBe initialLocation

        //and
        verifyNoMoreInteractions(mockRoutingCacheRepository)
      }
    }

    "return location passed as argument when no configuration found when sticky routing is disabled" in {
      running(FakeApplication(additionalConfiguration = createConfiguration())) {
        //given
        val locationName = "location-name"
        val initialLocation = mock[LocationType]
        when(initialLocation.name) thenReturn locationName
        implicit val mockRequest = FakeRequest()
        implicit val authContext = getAuthContext("")

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]

        //when
        val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(initialLocation, mock[AuditContext])

        //then
        await(returnedLocation) shouldBe initialLocation

        //and
        verifyNoMoreInteractions(mockRoutingCacheRepository)
      }
    }

    "return location passed as argument when configuration found but fallback not configured when sticky routing is disabled" in {

      val locationName = "location-name"

      running(FakeApplication(additionalConfiguration = createConfiguration(locationName = locationName, percentageBeToThrottled = 100, fallbackLocation = ""))) {
        //given
        val initialLocation = mock[LocationType]
        when(initialLocation.name) thenReturn locationName
        implicit val mockRequest = FakeRequest()
        implicit val authContext = getAuthContext("")

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]

        //when
        val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(initialLocation, mock[AuditContext])

        //then
        await(returnedLocation) shouldBe initialLocation

        //and
        verifyNoMoreInteractions(mockRoutingCacheRepository)

      }
    }

    "throttle to BTA welcome page when going to PTA welcome when sticky routing is disabled" in {
      running(FakeApplication(additionalConfiguration = createConfiguration(locationName = s"${PersonalTaxAccount.name}-gg", percentageBeToThrottled = 100, fallbackLocation = ""))) {
        //given
        val randomMock = mock[Random]
        when(randomMock.nextInt(100)) thenReturn 0
        implicit val mockRequest = FakeRequest()
        implicit val authContext = getAuthContext("")

        //and
        val auditContextMock = mock[AuditContext]

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]

        //and
        val throttlingServiceTest = new ThrottlingServiceTest(random = randomMock, routingCacheRepository = mockRoutingCacheRepository)

        //when
        val returnedLocation: Future[LocationType] = throttlingServiceTest.throttle(WelcomePTA, auditContextMock)

        //then
        await(returnedLocation) shouldBe WelcomeBTA

        //and
        verify(randomMock).nextInt(100)

        //and
        verifyNoMoreInteractions(mockRoutingCacheRepository)
      }
    }
  }

  it should {
    val initialLocation = mock[LocationType]
    val locationName = "location-name"
    val locationUrl = "location-url"
    when(initialLocation.name) thenReturn locationName
    when(initialLocation.url) thenReturn locationUrl

    val scenarios = evaluateUsingPlay {
      Table(
        ("scenario", "percentageBeToThrottled", "randomNumber", "expectedLocation", "throttled"),
        ("Should throttle to fallback when random number is less than percentage", 50, 10, BusinessTaxAccount, true),
        ("Should throttle to fallback when random number is equal than percentage", 50, 49, BusinessTaxAccount, true),
        ("Should not throttle to fallback when random number is equal than percentage", 50, 70, initialLocation, false)
      )
    }

    forAll(scenarios) { (scenario: String, percentageBeToThrottled: Int, randomNumber: Int, expectedLocation: LocationType, throttled: Boolean) =>
      s"return the right location after throttling or not and update audit context when sticky routing is disabled - scenario: $scenario" in {
        running(FakeApplication(additionalConfiguration = createConfiguration(locationName = locationName, percentageBeToThrottled = percentageBeToThrottled, fallbackLocation = expectedLocation.name))) {
          //given
          val randomMock = mock[Random]
          when(randomMock.nextInt(100)) thenReturn randomNumber
          implicit val mockRequest = FakeRequest()
          implicit val authContext = getAuthContext("")

          //and
          val auditContextMock = mock[AuditContext]

          //and
          val mockRoutingCacheRepository = mock[RoutingCacheRepository]

          //and
          val throttlingServiceTest = new ThrottlingServiceTest(random = randomMock, routingCacheRepository = mockRoutingCacheRepository)

          //when
          val returnedLocation: Future[LocationType] = throttlingServiceTest.throttle(initialLocation, auditContextMock)

          //then
          await(returnedLocation) shouldBe expectedLocation

          //and
          val throttlingAuditContext = ThrottlingAuditContext(throttlingPercentage = Some(percentageBeToThrottled), throttled = throttled, initialDestination = initialLocation, throttlingEnabled = throttlingServiceTest.throttlingEnabled, followingPreviouslyRoutedDestination = false)
          verify(auditContextMock).setThrottlingDetails(throttlingAuditContext)

          //and
          verify(randomMock).nextInt(100)

          //and
          verifyNoMoreInteractions(mockRoutingCacheRepository)
        }

      }

    }
  }

  it should {
    val configuration = evaluateUsingPlay {
      Map[String, Any](
            "throttling.enabled" -> true,
            "sticky-routing.enabled" -> true,
            s"throttling.locations.${PersonalTaxAccount.name}-gg.percentageBeToThrottled" -> 100,
            s"throttling.locations.${PersonalTaxAccount.name}-gg.fallback" -> BusinessTaxAccount.name,
            s"throttling.locations.${PersonalTaxAccount.name}-verify.percentageBeToThrottled" -> 100,
            s"throttling.locations.${PersonalTaxAccount.name}-verify.fallback" -> WelcomeBTA.name,
            "sticky-routing.short-live-cookie-duration" -> 1
          )
    }

    val scenarios = evaluateUsingPlay {
      Table(
        ("scenario", "tokenPresent", "expectedLocation", "cacheExpectedLocation"),
        ("Should throttle to BTA when token present", true, BusinessTaxAccount.name, BusinessTaxAccount.name),
        ("Should throttle to Welcome when token not present", false, WelcomeBTA.name, BusinessTaxAccount.name)
      )
    }

    forAll(scenarios) { (scenario: String, tokenPresent: Boolean, expectedLocation: String, cacheExpectedLocation: String) =>
      s"return the right location after throttling PTA or not when sticky routing is enabled but routingInfo is not present - scenario: $scenario" in {
        running(FakeApplication(additionalConfiguration = configuration)) {
          //given
          implicit lazy val fakeRequest = tokenPresent match {
            case false => FakeRequest()
            case true => FakeRequest().withSession(("token", "token"))
          }
          val utr = "utr"
          implicit val authContext = getAuthContext(utr)

          //and
          val mockRoutingCacheRepository = mock[RoutingCacheRepository]
          when(mockRoutingCacheRepository.findById(Id(utr))).thenReturn(Future(None))

          //and
          val mockWriteResult = mock[WriteResult]
          when(mockWriteResult.hasErrors).thenReturn(false)
          when(mockRoutingCacheRepository.insert(eqTo(Cache(Id(utr), Some(Json.toJson(RoutingInfo(utr, PersonalTaxAccount.name, cacheExpectedLocation))))))(any[ExecutionContext])).thenReturn(Future(mockWriteResult))

          //when
          val returnedLocation = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(PersonalTaxAccount, mock[AuditContext])

          //then
          await(returnedLocation).name shouldBe expectedLocation

          //and
          //TODO: check why mockito is saying the method is called twice
          verify(mockRoutingCacheRepository, atLeastOnce()).findById(Id(utr))
          verify(mockRoutingCacheRepository).insert(Cache(Id(utr), Some(Json.toJson(RoutingInfo(utr, PersonalTaxAccount.name, cacheExpectedLocation)))))
        }
      }
    }
  }

  val expirationDate: DateTime = DateTime.parse(longLiveCookieExpirationTime)

  val longLiveCookieInSeconds = 2 seconds

  override protected def beforeEach(): Unit = DateTimeUtils.setCurrentMillisFixed(expirationDate.getMillis - longLiveCookieInSeconds.toMillis)

  override protected def afterEach(): Unit = DateTimeUtils.setCurrentMillisSystem()

  it should {

    val scenarios = evaluateUsingPlay {
      Table(
        ("routedLocation", "throttledLocation", "expectedRoutedLocation", "expectedThrottledLocation", "cookieMaxAge"),
        (PersonalTaxAccount, PersonalTaxAccount, PersonalTaxAccount, PersonalTaxAccount, longLiveCookieInSeconds.toSeconds.toInt),
        (PersonalTaxAccount, BusinessTaxAccount, PersonalTaxAccount, BusinessTaxAccount, 1),
        (BusinessTaxAccount, BusinessTaxAccount, BusinessTaxAccount, BusinessTaxAccount, 1),
        (WelcomePTA, WelcomePTA, PersonalTaxAccount, PersonalTaxAccount, longLiveCookieInSeconds.toSeconds.toInt),
        (WelcomePTA, WelcomeBTA, PersonalTaxAccount, BusinessTaxAccount, 1),
        (WelcomeBTA, WelcomeBTA, BusinessTaxAccount, BusinessTaxAccount, 1)
      )
    }

    forAll(scenarios) { (routedLocation: LocationType, throttledLocation: LocationType, expectedRoutedLocation: LocationType, expectedThrottledLocation: LocationType, cookieMaxAge: Int) =>
      s"return the right location after throttling when sticky routing is enabled and routingInfo is present: routedLocation -> $routedLocation, throttledLocation -> $throttledLocation" in {
        running(FakeApplication(additionalConfiguration = createConfiguration(enabled = true, stickyRoutingEnabled = true))) {
          //given
          implicit lazy val fakeRequest = FakeRequest()
          val utr = "utr"
          implicit val authContext = getAuthContext(utr)

          //and
          val mockRoutingCacheRepository = mock[RoutingCacheRepository]
          when(mockRoutingCacheRepository.findById(Id(utr))).thenReturn(Future(Some(Cache(Id(utr), Some(Json.toJson(RoutingInfo(utr, routedLocation.name, throttledLocation.name)))))))

          //and
          val mockWriteResult = mock[WriteResult]
          when(mockWriteResult.hasErrors).thenReturn(false)
          when(mockRoutingCacheRepository.insert(eqTo(Cache(Id(utr), Some(Json.toJson(RoutingInfo(utr, expectedRoutedLocation.name, expectedThrottledLocation.name))))))(any[ExecutionContext])).thenReturn(Future(mockWriteResult))

          //when
          val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(routedLocation, mock[AuditContext])

          //then
          await(returnedLocation) shouldBe throttledLocation

          //and
          //TODO: check why mockito is saying the method is called twice
          verify(mockRoutingCacheRepository, atLeastOnce()).findById(Id(utr))
          verify(mockRoutingCacheRepository).insert(Cache(Id(utr), Some(Json.toJson(RoutingInfo(utr, expectedRoutedLocation.name, expectedThrottledLocation.name)))))
        }
      }
    }
  }

  it should {

    val scenarios = evaluateUsingPlay {
      Table(
        ("routedLocation", "cacheRoutedLocation", "cacheThrottledLocation", "cookieMaxAge"),
        (BusinessTaxAccount, PersonalTaxAccount, PersonalTaxAccount, 1),
        (BusinessTaxAccount, PersonalTaxAccount, BusinessTaxAccount, 1),
        (PersonalTaxAccount, BusinessTaxAccount, BusinessTaxAccount, longLiveCookieInSeconds.toSeconds.toInt),
        (BusinessTaxAccount, WelcomePTA, WelcomePTA, 1),
        (PersonalTaxAccount, WelcomePTA, WelcomeBTA, longLiveCookieInSeconds.toSeconds.toInt),
        (BusinessTaxAccount, WelcomeBTA, WelcomeBTA, 1)
      )
    }

    forAll(scenarios) { (routedLocation: LocationType, cacheRoutedLocation: LocationType, cacheThrottledLocation: LocationType, cookieMaxAge: Int) =>
      s"return the right location after throttling when sticky routing is enabled and cookie is present but routedLocation is different: routedLocation -> $cacheRoutedLocation, throttledLocation -> $cacheThrottledLocation" in {
        running(FakeApplication(additionalConfiguration = createConfiguration(enabled = true, stickyRoutingEnabled = true))) {
          //given
          implicit lazy val fakeRequest = FakeRequest()
          val utr = "utr"
          implicit val authContext = getAuthContext(utr)

          //and
          val mockRoutingCacheRepository = mock[RoutingCacheRepository]
          when(mockRoutingCacheRepository.findById(Id(utr))).thenReturn(Future(Some(Cache(Id(utr), Some(Json.toJson(RoutingInfo(utr, cacheRoutedLocation.name, cacheThrottledLocation.name)))))))

          //and
          val mockWriteResult = mock[WriteResult]
          when(mockWriteResult.hasErrors).thenReturn(false)
          when(mockRoutingCacheRepository.insert(eqTo(Cache(Id(utr), Some(Json.toJson(RoutingInfo(utr, routedLocation.name, routedLocation.name))))))(any[ExecutionContext])).thenReturn(Future(mockWriteResult))

          //when
          val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(routedLocation, mock[AuditContext])

          //then
          await(returnedLocation) shouldBe routedLocation

          //and
          //TODO: check why mockito is saying the method is called twice
          verify(mockRoutingCacheRepository, atLeastOnce()).findById(Id(utr))
          verify(mockRoutingCacheRepository).insert(Cache(Id(utr), Some(Json.toJson(RoutingInfo(utr, routedLocation.name, routedLocation.name)))))
        }
      }
    }
  }
}

class ThrottlingServiceTest(override val random: Random = Random, override val routingCacheRepository: RoutingCacheRepository) extends ThrottlingService

object RoutingCacheRepositoryTest extends MongoDbConnection {

  def apply(stubValue: Future[Option[Cache]]): RoutingCacheRepository = new RoutingCacheRepository {
    override def findById(id: Id, readPreference: ReadPreference)(implicit ec: ExecutionContext): Future[Option[Cache]] = {
      println("calling find by Id")
      stubValue
    }
  }

}
