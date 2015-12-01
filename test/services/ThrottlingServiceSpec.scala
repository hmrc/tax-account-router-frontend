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
import cryptography.Encryption
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
import play.api.{Configuration, GlobalSettings, Play}
import reactivemongo.api.ReadPreference
import repositories.RoutingCacheRepository
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.mongo.DatabaseUpdate
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
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

  val userId = "user-id"
  val encryptedUserId = "encrypted-user-id"

  def createConfiguration(enabled: Boolean = true, locationName: String = "default-location-name", percentageBeToThrottled: Int = 0, fallbackLocation: String = "default-fallback-location", stickyRoutingEnabled: Boolean = false) = {
    Map[String, Any](
      "throttling.enabled" -> enabled,
      s"throttling.locations.$locationName.percentageBeToThrottled" -> percentageBeToThrottled,
      s"throttling.locations.$locationName.fallback" -> fallbackLocation,
      "sticky-routing.enabled" -> stickyRoutingEnabled,
      "sticky-routing.long-live-cache-expiration-time" -> longLiveDocumentExpirationTime,
      "sticky-routing.short-live-cache-duration" -> shortLiveDocumentExpirationSeconds
    )
  }

  def authContextStub(userId: String): AuthContext = AuthContext(LoggedInUser(userId, None, None, None, ConfidenceLevel.L0), mock[Principal], None)

  "ThrottlingService" should {

    "not throttle if throttling disabled and sticky routing disabled" in {
      running(FakeApplication(additionalConfiguration = createConfiguration(enabled = false))) {
        //given
        val initialLocation = mock[LocationType]
        implicit val mockRequest = FakeRequest()
        implicit val authContext = authContextStub(userId)
        val mockAuditContext: AuditContext = mock[AuditContext]

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]
        val mockEncryption = Mocks.encryption(userId, encryptedUserId)

        //when
        val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository, encryption = mockEncryption).throttle(initialLocation, mockAuditContext)

        //then
        await(returnedLocation) shouldBe initialLocation

        //and
        verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(None, false, initialLocation, false, false))

        //and
        verifyNoMoreInteractions(mockRoutingCacheRepository)
      }
    }

    "return location passed as argument when no configuration found when sticky routing is disabled" in {
      val configuration = createConfiguration()
      running(FakeApplication(additionalConfiguration = configuration)) {
        //given
        val locationName = "location-name"
        val initialLocation = mock[LocationType]
        when(initialLocation.name) thenReturn locationName
        implicit val mockRequest = FakeRequest()
        implicit val authContext = authContextStub(userId)
        val mockAuditContext: AuditContext = mock[AuditContext]

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]
        val mockEncryption = Mocks.encryption(userId, encryptedUserId)

        val mockHourlyLimitService = Mocks.mockHourlyLimitService()
        val configurationForLocation = Configuration.empty
        when(
          mockHourlyLimitService.applyHourlyLimit(
            eqTo(initialLocation), eqTo(initialLocation), eqTo(encryptedUserId), eqTo(configurationForLocation)
          )(any[ExecutionContext])
        ).thenReturn(Future(initialLocation))

        //when
        val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository, encryption = mockEncryption, hourlyLimitService = mockHourlyLimitService).throttle(initialLocation, mockAuditContext)

        //then
        await(returnedLocation) shouldBe initialLocation

        //and
        verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(None, false, initialLocation, true, false))

        //and
        verify(mockHourlyLimitService).applyHourlyLimit(eqTo(initialLocation), eqTo(initialLocation), eqTo(encryptedUserId), eqTo(configurationForLocation))(any[ExecutionContext])

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
        implicit val authContext = authContextStub(userId)
        val mockAuditContext: AuditContext = mock[AuditContext]

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]
        val mockEncryption = Mocks.encryption(userId, encryptedUserId)

        //when
        val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository, encryption = mockEncryption).throttle(initialLocation, mockAuditContext)

        //then
        await(returnedLocation) shouldBe initialLocation

        //and
        verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(Some(100), false, initialLocation, true, false))

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
          implicit val authContext = authContextStub(userId)

          //and
          val auditContextMock = mock[AuditContext]

          //and
          val mockHourlyLimitService = Mocks.mockHourlyLimitService()

          import play.api.Play.current
          val configurationForLocation = Play.configuration.getConfig(s"throttling.locations.$locationName").getOrElse(Configuration.empty)

          when(
            mockHourlyLimitService.applyHourlyLimit(
              eqTo(initialLocation), eqTo(initialLocation), eqTo(encryptedUserId), eqTo(configurationForLocation)
            )(any[ExecutionContext])
          ).thenReturn(Future(initialLocation))

          //and
          val mockRoutingCacheRepository = mock[RoutingCacheRepository]
          val mockEncryption = Mocks.encryption(userId, encryptedUserId)

          //and
          val throttlingServiceTest = new ThrottlingServiceTest(random = randomMock, routingCacheRepository = mockRoutingCacheRepository, encryption = mockEncryption, hourlyLimitService = mockHourlyLimitService)

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
            s"throttling.locations.${PersonalTaxAccount.name}-verify.fallback" -> BusinessTaxAccount.name,
            "sticky-routing.short-live-cache-duration" -> shortLiveDocumentExpirationSeconds,
            "sticky-routing.long-live-cache-expiration-time" -> longLiveDocumentExpirationTime
          )
    }

    val scenarios = evaluateUsingPlay {
      Table(
        ("scenario", "tokenPresent", "expectedLocation"),
        ("Should throttle to BTA when token present", true, BusinessTaxAccount.name),
        ("Should throttle to BTA when token not present", false, BusinessTaxAccount.name)
      )
    }

    forAll(scenarios) { (scenario: String, tokenPresent: Boolean, expectedLocation: String) =>
      s"return the right location after throttling PTA or not when sticky routing is enabled but routingInfo is not present - scenario: $scenario" in {
        running(FakeApplication(additionalConfiguration = configuration)) {
          //given
          implicit lazy val fakeRequest = tokenPresent match {
            case false => FakeRequest()
            case true => FakeRequest().withSession(("token", "token"))
          }
          implicit val authContext = authContextStub(userId)
          val id = Id(encryptedUserId)
          val mockAuditContext = mock[AuditContext]

          //and
          val mockRoutingCacheRepository = mock[RoutingCacheRepository]
          when(mockRoutingCacheRepository.findById(id)).thenReturn(Future(None))

          //and
          val mockDatabaseUpdateResult = mock[Future[DatabaseUpdate[Cache]]]
          val expectedExpirationTime: DateTime = DateTime.now(DateTimeZone.UTC).plusSeconds(shortLiveDocumentExpirationSeconds)
          when(mockRoutingCacheRepository.createOrUpdate(id, "routingInfo", Json.toJson(RoutingInfo(PersonalTaxAccount.name, expectedLocation, expectedExpirationTime)))).thenReturn(Future(mockDatabaseUpdateResult))

          val mockEncryption = Mocks.encryption(userId, encryptedUserId)

          //when
          val returnedLocation = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository, encryption = mockEncryption).throttle(PersonalTaxAccount, mockAuditContext)

          //then
          await(returnedLocation).name shouldBe expectedLocation

          //and
          verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(Some(100), true, PersonalTaxAccount, true, false))

          //and
          verify(mockRoutingCacheRepository).findById(eqTo(id), any[ReadPreference])(any[ExecutionContext])
          verify(mockRoutingCacheRepository).createOrUpdate(id, "routingInfo", Json.toJson(RoutingInfo(PersonalTaxAccount.name, expectedLocation, expectedExpirationTime)))
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
            "sticky-routing.short-live-cache-duration" -> shortLiveDocumentExpirationSeconds,
            "sticky-routing.long-live-cache-expiration-time" -> longLiveDocumentExpirationTime
          )
    }

    "throttle when the cache document is expired" in {
      running(FakeApplication(additionalConfiguration = configuration)) {
        //given
        implicit val fakeRequest = FakeRequest().withSession(("token", "token"))
        implicit val authContext = authContextStub(userId)
        val id = Id(encryptedUserId)
        val mockAuditContext = mock[AuditContext]

        //and
        val mockRoutingCacheRepository = mock[RoutingCacheRepository]
        when(mockRoutingCacheRepository.findById(id)).thenReturn(Future(Some(Cache(id, Some(Json.obj("routingInfo" -> Json.toJson(RoutingInfo(PersonalTaxAccount.name, PersonalTaxAccount.name, DateTime.now(DateTimeZone.UTC).minusHours(1)))))))))

        //and
        val mockDatabaseUpdateResult = mock[Future[DatabaseUpdate[Cache]]]
        val expectedExpirationTime = fixedDateTime.plusSeconds(shortLiveDocumentExpirationSeconds)
        when(mockRoutingCacheRepository.createOrUpdate(id, "routingInfo", Json.toJson(RoutingInfo(PersonalTaxAccount.name, BusinessTaxAccount.name, expectedExpirationTime)))).thenReturn(Future(mockDatabaseUpdateResult))

        val mockEncryption = Mocks.encryption(userId, encryptedUserId)

        //when
        val returnedLocation = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository, encryption = mockEncryption).throttle(PersonalTaxAccount, mockAuditContext)

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

  it should {

    val expectedShortExpirationTime: DateTime = fixedDateTime.plusSeconds(shortLiveDocumentExpirationSeconds)

    val scenarios = evaluateUsingPlay {
      Table(
        ("routedLocation", "throttledLocation", "expectedExpirationTime"),
        (PersonalTaxAccount, PersonalTaxAccount, DateTime.parse(longLiveDocumentExpirationTime)),
        (PersonalTaxAccount, BusinessTaxAccount, expectedShortExpirationTime),
        (BusinessTaxAccount, BusinessTaxAccount, expectedShortExpirationTime)
      )
    }

    forAll(scenarios) { (routedLocation: LocationType, throttledLocation: LocationType, expectedExpirationTime: DateTime) =>
      s"return the right location after throttling when sticky routing is enabled and routingInfo is present: routedLocation -> $routedLocation, throttledLocation -> $throttledLocation" in {
        running(FakeApplication(additionalConfiguration = createConfiguration(enabled = true, stickyRoutingEnabled = true), withGlobal = Some(new GlobalSettingsTest()))) {
          //given
          implicit lazy val fakeRequest = FakeRequest()
          implicit val authContext = authContextStub(userId)
          val id = Id(encryptedUserId)
          val mockAuditContext = mock[AuditContext]

          //and
          val mockRoutingCacheRepository = mock[RoutingCacheRepository]
          when(mockRoutingCacheRepository.findById(id)).thenReturn(Future(Some(Cache(id, Some(Json.obj("routingInfo" -> Json.toJson(RoutingInfo(routedLocation.name, throttledLocation.name, expectedExpirationTime))))))))

          //and
          val mockDatabaseUpdateResult = mock[Future[DatabaseUpdate[Cache]]]
          when(mockRoutingCacheRepository.createOrUpdate(id, "routingInfo", Json.toJson(RoutingInfo(routedLocation.name, throttledLocation.name, expectedExpirationTime)))).thenReturn(Future(mockDatabaseUpdateResult))

          val mockEncryption = Mocks.encryption(userId, encryptedUserId)

          //when
          val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository, encryption = mockEncryption).throttle(routedLocation, mockAuditContext)

          //then
          await(returnedLocation) shouldBe throttledLocation

          //and
          verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(None, routedLocation != throttledLocation, routedLocation, throttlingEnabled = true, followingPreviouslyRoutedDestination = true))

          //and
          verify(mockRoutingCacheRepository).findById(eqTo(id), any[ReadPreference])(any[ExecutionContext])
          verify(mockRoutingCacheRepository).createOrUpdate(id, "routingInfo", Json.toJson(RoutingInfo(routedLocation.name, throttledLocation.name, expectedExpirationTime)))
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
        (PersonalTaxAccount, BusinessTaxAccount, BusinessTaxAccount, DateTime.parse(longLiveDocumentExpirationTime))
      )
    }

    forAll(scenarios) { (routedLocation: LocationType, cacheRoutedLocation: LocationType, cacheThrottledLocation: LocationType, expectedExpirationTime: DateTime) =>
      s"return the right location after throttling when sticky routing is enabled and cache is present but routedLocation is different: routedLocation -> $cacheRoutedLocation, throttledLocation -> $cacheThrottledLocation" in {
        running(FakeApplication(additionalConfiguration = createConfiguration(enabled = true, stickyRoutingEnabled = true), withGlobal = Some(new GlobalSettingsTest()))) {
          //given
          implicit lazy val fakeRequest = FakeRequest()
          implicit val authContext = authContextStub(userId)
          val id = Id(encryptedUserId)
          val mockAuditContext = mock[AuditContext]

          //and
          val mockRoutingCacheRepository = mock[RoutingCacheRepository]
          when(mockRoutingCacheRepository.findById(id)).thenReturn(Future(Some(Cache(id, Some(Json.obj("routingInfo" -> Json.toJson(RoutingInfo(cacheRoutedLocation.name, cacheThrottledLocation.name, expectedExpirationTime))))))))

          //and
          val mockDatabaseUpdateResult = mock[Future[DatabaseUpdate[Cache]]]
          when(mockRoutingCacheRepository.createOrUpdate(id, "routingInfo", Json.toJson(RoutingInfo(routedLocation.name, routedLocation.name, expectedExpirationTime)))).thenReturn(Future(mockDatabaseUpdateResult))

          val mockEncryption = Mocks.encryption(userId, encryptedUserId)

          //and
          val mockHourlyLimitService = Mocks.mockHourlyLimitService()

          import play.api.Play.current
          val configurationForLocation = Play.configuration.getConfig(s"throttling.locations.$routedLocation").getOrElse(Configuration.empty)

          when(
            mockHourlyLimitService.applyHourlyLimit(
              eqTo(routedLocation), eqTo(routedLocation), eqTo(encryptedUserId), eqTo(configurationForLocation)
            )(any[ExecutionContext])
          ).thenReturn(Future(routedLocation))

          //when
          val returnedLocation: Future[LocationType] = new ThrottlingServiceTest(routingCacheRepository = mockRoutingCacheRepository, encryption = mockEncryption, hourlyLimitService = mockHourlyLimitService).throttle(routedLocation, mockAuditContext)

          //then
          await(returnedLocation) shouldBe routedLocation

          //and
          verify(mockAuditContext).setThrottlingDetails(ThrottlingAuditContext(None, false, routedLocation, true, false))

          //and
          verify(mockRoutingCacheRepository).findById(eqTo(id), any[ReadPreference])(any[ExecutionContext])
          verify(mockRoutingCacheRepository).createOrUpdate(id, "routingInfo", Json.toJson(RoutingInfo(routedLocation.name, routedLocation.name, expectedExpirationTime)))

        }
      }
    }
  }
}

object Mocks extends MockitoSugar {
  def encryption(stringToEncrypt: String, encryptedString: String): Encryption = {
    val mockEncryption: Encryption = mock[Encryption]
    when(mockEncryption.getSha256(stringToEncrypt)).thenReturn(encryptedString)
    mockEncryption
  }

  def mockHourlyLimitService(): HourlyLimitService = mock[HourlyLimitService]
}

class ThrottlingServiceTest(override val random: Random = Random,
                            override val routingCacheRepository: RoutingCacheRepository,
                            override val encryption: Encryption,
                            override val hourlyLimitService: HourlyLimitService = Mocks.mockHourlyLimitService()) extends ThrottlingService

class GlobalSettingsTest extends GlobalSettings {

  override def onLoadConfig(config: Configuration, path: File, classloader: ClassLoader, mode: Mode): Configuration = {
    //remove throttling element in configuration taken from application.conf
    val configuration: Configuration = Configuration(ConfigFactory.load(config.underlying).withoutPath("throttling"))
    super.onLoadConfig(configuration, path, classloader, mode)
  }

}