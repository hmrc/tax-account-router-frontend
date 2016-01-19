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

package services

import helpers.SpecHelpers
import model.Location
import org.joda.time.{DateTime, DateTimeUtils}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.Configuration
import repositories.{HourlyLimitId, HourlyLimitsCacheRepository}
import uk.gov.hmrc.cache.model.Cache
import uk.gov.hmrc.mongo.DatabaseUpdate
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class HourlyLimitServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication with SpecHelpers with BeforeAndAfterAll {

  val fixedDateTime = DateTime.now()

  val location = evaluateUsingPlay { Location("location-url", "location-name") }
  val fallback = evaluateUsingPlay { Location("location-fallback", "location-fallback") }

  override def beforeAll(): Unit = {
    DateTimeUtils.setCurrentMillisFixed(fixedDateTime.getMillis)
  }

  override def afterAll(): Unit = {
    DateTimeUtils.setCurrentMillisSystem()
  }

  "HourlyLimitServiceSpec" should {

    "return location when empty config" in {
      //given
      val mockHourlyLimitsCacheRepository = mock[HourlyLimitsCacheRepository]

      //when
      val futureLocation: Future[Location] = new HourlyLimitServiceTest(mockHourlyLimitsCacheRepository).applyHourlyLimit(location, fallback, "userId", Configuration.empty)

      //then
      await(futureLocation) shouldBe location

      //and
      verifyNoMoreInteractions(mockHourlyLimitsCacheRepository)
    }

    "return location when no config found" in {
      //given
      val configuration = Configuration.from(Map(
        s"hourlyLimit.${fixedDateTime.getHourOfDay + 1}" -> 1
      ))

      val mockHourlyLimitsCacheRepository = mock[HourlyLimitsCacheRepository]

      //when
      val futureLocation: Future[Location] = new HourlyLimitServiceTest(mockHourlyLimitsCacheRepository).applyHourlyLimit(location, fallback, "userId", configuration)

      //then
      await(futureLocation) shouldBe location

      //and
      verifyNoMoreInteractions(mockHourlyLimitsCacheRepository)
    }
  }

  it should {

    val limit = 1
    val configurationFound = Configuration.from(Map(
      s"hourlyLimit.${fixedDateTime.getHourOfDay}" -> limit
    ))
    val configurationWithDefault = Configuration.from(Map(
      s"hourlyLimit.${fixedDateTime.getHourOfDay + 1}" -> 100,
      s"hourlyLimit.other" -> limit
    ))

    val scenarios = evaluateUsingPlay {
      Table(
        ("scenario", "configuration"),
        ("config found", configurationFound),
        ("config not found but returned default one", configurationWithDefault)
      )
    }

    forAll(scenarios) { (scenario: String, configuration: Configuration) =>
      s"return location when $scenario and repository returns update operation" in {
        //given
        val userId = "userId"

        //and
        val hourlyLimitId: HourlyLimitId = HourlyLimitId(location, fixedDateTime.getHourOfDay)
        val mockHourlyLimitsCacheRepository = mock[HourlyLimitsCacheRepository]
        when(mockHourlyLimitsCacheRepository.createOrUpdate(hourlyLimitId, limit, userId)).thenReturn(Future(Some(mock[DatabaseUpdate[Cache]])))

        //when
        val futureLocation: Future[Location] = new HourlyLimitServiceTest(mockHourlyLimitsCacheRepository).applyHourlyLimit(location, fallback, userId, configuration)

        //then
        await(futureLocation) shouldBe location

        //and
        verify(mockHourlyLimitsCacheRepository).createOrUpdate(eqTo(hourlyLimitId), eqTo(limit), eqTo(userId))(any[ExecutionContext])
        verifyNoMoreInteractions(mockHourlyLimitsCacheRepository)
      }
    }
  }

  it should {

    val scenarios = evaluateUsingPlay {
      Table(
        ("scenario", "userExistsInLimit", "expectedLocation"),
        ("user exists and returns location", true, location),
        ("user does not exist and returs fallback", false, fallback)
      )
    }

    forAll(scenarios) { (scenario: String, userExistsInLimit: Boolean, expectedLocation: Location) =>
      s"when default config found and repository returns none update operation and $scenario" in {
        //given
        val limit = 1
        val configuration = Configuration.from(Map(
          s"hourlyLimit.${fixedDateTime.getHourOfDay}" -> limit
        ))
        val userId = "userId"

        //and
        val hourlyLimitId: HourlyLimitId = HourlyLimitId(location, fixedDateTime.getHourOfDay)
        val mockHourlyLimitsCacheRepository = mock[HourlyLimitsCacheRepository]
        when(mockHourlyLimitsCacheRepository.createOrUpdate(hourlyLimitId, limit, userId)).thenReturn(Future(None))
        when(mockHourlyLimitsCacheRepository.exists(hourlyLimitId, userId)).thenReturn(Future(userExistsInLimit))

        //when
        val futureLocation: Future[Location] = new HourlyLimitServiceTest(mockHourlyLimitsCacheRepository).applyHourlyLimit(location, fallback, userId, configuration)

        //then
        await(futureLocation) shouldBe expectedLocation

        //and
        verify(mockHourlyLimitsCacheRepository).createOrUpdate(eqTo(hourlyLimitId), eqTo(limit), eqTo(userId))(any[ExecutionContext])
        verify(mockHourlyLimitsCacheRepository).exists(eqTo(hourlyLimitId), eqTo(userId))(any[ExecutionContext])
        verifyNoMoreInteractions(mockHourlyLimitsCacheRepository)
      }
    }

  }

  class HourlyLimitServiceTest(override val hourlyLimitsCacheRepository: HourlyLimitsCacheRepository) extends HourlyLimitService


}
