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

import java.io.File

import com.typesafe.config.ConfigFactory
import helpers.SpecHelpers
import model.Location._
import model.{AuditContext, RoutingInfo, ThrottlingAuditContext}
import org.joda.time.{DateTime, DateTimeUtils, DateTimeZone}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.Mode.Mode
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeRequest}
import play.api.{Configuration, GlobalSettings}
import reactivemongo.api.ReadPreference
import repositories.RoutingCacheRepository
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.mongo.DatabaseUpdate
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, ConfidenceLevel, SaAccount}
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class ThrottlingServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterAll with SpecHelpers {

  private val longLiveDocumentExpirationTime: String = "2016-02-15T00:00"
  private val shortLiveDocumentExpirationSeconds: Int = 1

  val expirationDate: DateTime = DateTime.parse(longLiveDocumentExpirationTime)

  val fixedDateTime = DateTime.now().withZone(DateTimeZone.UTC)

  override protected def beforeAll(): Unit = {
    DateTimeUtils.setCurrentMillisFixed(fixedDateTime.getMillis)
  }

  override protected def afterAll(): Unit = {
    DateTimeUtils.setCurrentMillisSystem()
  }

  def createConfiguration(enabled: Boolean = true, locationName: String = "default-location-name", percentageBeToThrottled: Int = 0, fallbackLocation: String = "default-fallback-location", stickyRoutingEnabled: Boolean = false) = {
    Map[String, Any](
      "throttling.enabled" -> enabled,
      s"throttling.locations.$locationName.percentageBeToThrottled" -> percentageBeToThrottled,
      s"throttling.locations.$locationName.fallback" -> fallbackLocation,
      "sticky-routing.enabled" -> stickyRoutingEnabled,
      "sticky-routing.long-live-cookie-expiration-time" -> longLiveDocumentExpirationTime,
      "sticky-routing.short-live-cookie-duration" -> shortLiveDocumentExpirationSeconds
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
        val mockAuditContext: AuditContext = mock[AuditContext]

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]

        //when
        val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(initialLocation, mockAuditContext)

        //then
        await(returnedLocation) shouldBe initialLocation

        //and
        verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(None, false, initialLocation, false, false))

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
        val mockAuditContext: AuditContext = mock[AuditContext]

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]

        //when
        val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(initialLocation, mockAuditContext)

        //then
        await(returnedLocation) shouldBe initialLocation

        //and
        verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(None, false, initialLocation, true, false))

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
        val mockAuditContext: AuditContext = mock[AuditContext]

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]

        //when
        val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(initialLocation, mockAuditContext)

        //then
        await(returnedLocation) shouldBe initialLocation

        //and
        verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(Some(100), false, initialLocation, true, false))

        //and
        verifyNoMoreInteractions(mockRoutingCacheRepository)

      }
    }

    "throttle to BTA welcome page when going to PTA welcome when sticky routing is disabled" in {

      running(FakeApplication(additionalConfiguration = createConfiguration(locationName = s"${PersonalTaxAccount.group}-gg", percentageBeToThrottled = 100, fallbackLocation = BusinessTaxAccount.name), withGlobal = Some(new GlobalSettingsTest()))) {
        //given
        val randomMock = mock[Random]
        when(randomMock.nextInt(100)) thenReturn 0
        implicit val mockRequest = FakeRequest().withSession(("token", "token"))
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
        verify(auditContextMock).setThrottlingDetails(ThrottlingAuditContext(Some(100), true, WelcomePTA, true, false))

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
            "sticky-routing.short-live-cookie-duration" -> shortLiveDocumentExpirationSeconds,
            "sticky-routing.long-live-cookie-expiration-time" -> longLiveDocumentExpirationTime
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
          val mockAuditContext = mock[AuditContext]

          //and
          val mockRoutingCacheRepository = mock[RoutingCacheRepository]
          when(mockRoutingCacheRepository.findById(Id(utr))).thenReturn(Future(None))

          //and
          val mockDatabaseUpdateResult = mock[Future[DatabaseUpdate[Cache]]]
          val expectedExpirationTime: DateTime = DateTime.now(DateTimeZone.UTC).plusSeconds(shortLiveDocumentExpirationSeconds)
          when(mockRoutingCacheRepository.createOrUpdate(Id(utr), "routingInfo", Json.toJson(RoutingInfo(PersonalTaxAccount.name, cacheExpectedLocation, expectedExpirationTime)))).thenReturn(Future(mockDatabaseUpdateResult))

          //when
          val returnedLocation = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(PersonalTaxAccount, mockAuditContext)

          //then
          await(returnedLocation).name shouldBe expectedLocation

          //and
          verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(Some(100), true, PersonalTaxAccount, true, false))

          //and
          verify(mockRoutingCacheRepository).findById(eqTo(Id(utr)), any[ReadPreference])(any[ExecutionContext])
          verify(mockRoutingCacheRepository).createOrUpdate(Id(utr), "routingInfo", Json.toJson(RoutingInfo(PersonalTaxAccount.name, cacheExpectedLocation, expectedExpirationTime)))
        }
      }
    }
  }

  it should {

    // configuration to always throttle PTA to BTA
    val configuration = evaluateUsingPlay {
      Map[String, Any](
            "throttling.enabled" -> true,
            "sticky-routing.enabled" -> true,
            s"throttling.locations.${PersonalTaxAccount.name}-gg.percentageBeToThrottled" -> 100,
            s"throttling.locations.${PersonalTaxAccount.name}-gg.fallback" -> BusinessTaxAccount.name,
            "sticky-routing.short-live-cookie-duration" -> shortLiveDocumentExpirationSeconds,
            "sticky-routing.long-live-cookie-expiration-time" -> longLiveDocumentExpirationTime
          )
    }

    "throttle when the cache document is expired" in {
      running(FakeApplication(additionalConfiguration = configuration)) {
        //given
        implicit val fakeRequest = FakeRequest().withSession(("token", "token"))
        val utr = "utr"
        implicit val authContext = getAuthContext(utr)
        val id = Id(utr)
        val mockAuditContext = mock[AuditContext]

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]
        when(mockRoutingCacheRepository.findById(id)).thenReturn(Future(Some(Cache(id, Some(Json.obj("routingInfo" -> Json.toJson(RoutingInfo(PersonalTaxAccount.name, PersonalTaxAccount.name, DateTime.now(DateTimeZone.UTC).minusHours(1)))))))))

        //and
        val mockDatabaseUpdateResult = mock[Future[DatabaseUpdate[Cache]]]
        val expectedExpirationTime = fixedDateTime.plusSeconds(shortLiveDocumentExpirationSeconds)
        when(mockRoutingCacheRepository.createOrUpdate(id, "routingInfo", Json.toJson(RoutingInfo(PersonalTaxAccount.name, BusinessTaxAccount.name, expectedExpirationTime)))).thenReturn(Future(mockDatabaseUpdateResult))


        //when
        val returnedLocation = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(PersonalTaxAccount, mockAuditContext)

        //then
        await(returnedLocation).name shouldBe BusinessTaxAccount.name

        //and
        verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(Some(100), true, PersonalTaxAccount, true, false))

        //and
        verify(mockRoutingCacheRepository).findById(eqTo(id), any[ReadPreference])(any[ExecutionContext])
        verify(mockRoutingCacheRepository).createOrUpdate(id, "routingInfo", Json.toJson(RoutingInfo(PersonalTaxAccount.name, BusinessTaxAccount.name, expectedExpirationTime)))

      }
    }
  }

  val longLiveCookieInSeconds = 2 seconds

  it should {

    val expectedShortExpirationTime: DateTime = fixedDateTime.plusSeconds(shortLiveDocumentExpirationSeconds)

    val scenarios = evaluateUsingPlay {
      Table(
        ("routedLocation", "throttledLocation", "expectedRoutedLocation", "expectedThrottledLocation", "expectedExpirationTime"),
        (PersonalTaxAccount, PersonalTaxAccount, PersonalTaxAccount, PersonalTaxAccount, DateTime.parse(longLiveDocumentExpirationTime)),
        (PersonalTaxAccount, BusinessTaxAccount, PersonalTaxAccount, BusinessTaxAccount, expectedShortExpirationTime),
        (BusinessTaxAccount, BusinessTaxAccount, BusinessTaxAccount, BusinessTaxAccount, expectedShortExpirationTime),
        (WelcomePTA, WelcomePTA, PersonalTaxAccount, PersonalTaxAccount, DateTime.parse(longLiveDocumentExpirationTime)),
        (WelcomePTA, WelcomeBTA, PersonalTaxAccount, BusinessTaxAccount, expectedShortExpirationTime),
        (WelcomeBTA, WelcomeBTA, BusinessTaxAccount, BusinessTaxAccount, expectedShortExpirationTime)
      )
    }

    forAll(scenarios) { (routedLocation: LocationType, throttledLocation: LocationType, expectedRoutedLocation: LocationType, expectedThrottledLocation: LocationType, expectedExpirationTime: DateTime) =>
      s"return the right location after throttling when sticky routing is enabled and routingInfo is present: routedLocation -> $routedLocation, throttledLocation -> $throttledLocation" in {
        running(FakeApplication(additionalConfiguration = createConfiguration(enabled = true, stickyRoutingEnabled = true), withGlobal = Some(new GlobalSettingsTest()))) {
          //given
          implicit lazy val fakeRequest = FakeRequest()
          val utr = "utr"
          implicit val authContext = getAuthContext(utr)
          val mockAuditContext = mock[AuditContext]

          val id = Id(utr)

          //and
          val mockRoutingCacheRepository = mock[RoutingCacheRepository]
          when(mockRoutingCacheRepository.findById(id)).thenReturn(Future(Some(Cache(id, Some(Json.obj("routingInfo" -> Json.toJson(RoutingInfo(routedLocation.name, throttledLocation.name, expectedExpirationTime))))))))

          //and
          val mockDatabaseUpdateResult = mock[Future[DatabaseUpdate[Cache]]]
          when(mockRoutingCacheRepository.createOrUpdate(id, "routingInfo", Json.toJson(RoutingInfo(expectedRoutedLocation.name, expectedThrottledLocation.name, expectedExpirationTime)))).thenReturn(Future(mockDatabaseUpdateResult))


          //when
          val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(routedLocation, mockAuditContext)

          //then
          await(returnedLocation) shouldBe throttledLocation

          //and
          verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(None, routedLocation != throttledLocation, routedLocation, true, true))

          //and
          verify(mockRoutingCacheRepository).findById(eqTo(id), any[ReadPreference])(any[ExecutionContext])
          verify(mockRoutingCacheRepository).createOrUpdate(Id(utr), "routingInfo", Json.toJson(RoutingInfo(expectedRoutedLocation.name, expectedThrottledLocation.name, expectedExpirationTime)))
        }
      }
    }
  }

  it should {

    val expectedShortExpirationTime: DateTime = fixedDateTime.plusSeconds(shortLiveDocumentExpirationSeconds)

    val scenarios = evaluateUsingPlay {
      Table(
        ("routedLocation", "cacheRoutedLocation", "cacheThrottledLocation", "expectedExpirationTime"),
        (BusinessTaxAccount, PersonalTaxAccount, PersonalTaxAccount, expectedShortExpirationTime),
        (BusinessTaxAccount, PersonalTaxAccount, BusinessTaxAccount, expectedShortExpirationTime),
        (PersonalTaxAccount, BusinessTaxAccount, BusinessTaxAccount, DateTime.parse(longLiveDocumentExpirationTime)),
        (BusinessTaxAccount, WelcomePTA, WelcomePTA, expectedShortExpirationTime),
        (PersonalTaxAccount, WelcomePTA, WelcomeBTA, DateTime.parse(longLiveDocumentExpirationTime)),
        (BusinessTaxAccount, WelcomeBTA, WelcomeBTA, expectedShortExpirationTime)
      )
    }

    forAll(scenarios) { (routedLocation: LocationType, cacheRoutedLocation: LocationType, cacheThrottledLocation: LocationType, expectedExpirationTime: DateTime) =>
      s"return the right location after throttling when sticky routing is enabled and cookie is present but routedLocation is different: routedLocation -> $cacheRoutedLocation, throttledLocation -> $cacheThrottledLocation" in {
        running(FakeApplication(additionalConfiguration = createConfiguration(enabled = true, stickyRoutingEnabled = true), withGlobal = Some(new GlobalSettingsTest()))) {
          //given
          implicit lazy val fakeRequest = FakeRequest()
          val utr = "utr"
          implicit val authContext = getAuthContext(utr)
          val mockAuditContext = mock[AuditContext]

          val id = Id(utr)

          //and
          val mockRoutingCacheRepository = mock[RoutingCacheRepository]
          when(mockRoutingCacheRepository.findById(id)).thenReturn(Future(Some(Cache(id, Some(Json.obj("routingInfo" -> Json.toJson(RoutingInfo(cacheRoutedLocation.name, cacheThrottledLocation.name, expectedExpirationTime))))))))

          //and
          val mockDatabaseUpdateResult = mock[Future[DatabaseUpdate[Cache]]]
          when(mockRoutingCacheRepository.createOrUpdate(id, "routingInfo", Json.toJson(RoutingInfo(routedLocation.name, routedLocation.name, expectedExpirationTime)))).thenReturn(Future(mockDatabaseUpdateResult))

          //when
          val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository).throttle(routedLocation, mockAuditContext)

          //then
          await(returnedLocation) shouldBe routedLocation

          //and
          verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(None, false, routedLocation, true, false))

          //and
          verify(mockRoutingCacheRepository).findById(eqTo(id), any[ReadPreference])(any[ExecutionContext])
          verify(mockRoutingCacheRepository).createOrUpdate(Id(utr), "routingInfo", Json.toJson(RoutingInfo(routedLocation.name, routedLocation.name, expectedExpirationTime)))

        }
      }
    }
  }
}

class ThrottlingServiceTest(override val random: Random = Random, override val routingCacheRepository: RoutingCacheRepository) extends ThrottlingService

class GlobalSettingsTest extends GlobalSettings {

  override def onLoadConfig(config: Configuration, path: File, classloader: ClassLoader, mode: Mode): Configuration = {
    //remove throttling element in configuration taken from application.conf
    val configuration: Configuration = Configuration(ConfigFactory.load(config.underlying).withoutPath("throttling"))
    super.onLoadConfig(configuration, path, classloader, mode)
  }

}