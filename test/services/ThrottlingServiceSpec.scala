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

import model.Location
import model.Location.LocationType
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.test.{FakeApplication, Helpers}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.util.Random

class ThrottlingServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication{

  def createConfiguration(enabled: Boolean = true, locationName: String = "default-location-name", percentageBeToThrottled: Float = 0, fallbackLocation : String = "default-fallback-location") = {
    Map[String, Any](
      "throttling.enabled" -> enabled,
      s"throttling.locations.$locationName.percentageBeToThrottled" -> percentageBeToThrottled,
      s"throttling.locations.$locationName.fallback" -> fallbackLocation
    )
  }

  "ThrottlingService" should {

    "not throttle if disabled" in {
      Helpers.running(FakeApplication(additionalConfiguration = createConfiguration(enabled = false))) {
        //given
        val initialLocation = mock[LocationType]

        //when
        val returnedLocation: LocationType = ThrottlingService.throttle(initialLocation)

        //then
        returnedLocation shouldBe initialLocation
      }
    }

    "return location passed as argument when no configuration found" in {
      Helpers.running(FakeApplication(additionalConfiguration = createConfiguration())) {
        //given
        val locationName = "location-name"
        val initialLocation = mock[LocationType]
        when(initialLocation.name) thenReturn locationName

        //when
        val returnedLocation: LocationType = ThrottlingService.throttle(initialLocation)

        //then
        returnedLocation shouldBe initialLocation
      }
    }

    "return location passed as argument when configuration found but fallback not configured" in {

      val locationName = "location-name"

      Helpers.running(FakeApplication(additionalConfiguration = createConfiguration(locationName = locationName, percentageBeToThrottled = 1, fallbackLocation = ""))) {
        //given
        val initialLocation = mock[LocationType]
        when(initialLocation.name) thenReturn locationName

        //when
        val returnedLocation: LocationType = ThrottlingService.throttle(initialLocation)

        //then
        returnedLocation shouldBe initialLocation
      }
    }

    "return the right location after throttling or not" in {

      val initialLocation = mock[LocationType]
      val locationName = "location-name"
      when(initialLocation.name) thenReturn locationName

      val scenarios = Table(
        ("scenario", "percentageBeToThrottled", "randomNumber", "expectedLocation"),
        ("Should throttle to fallback when random number is less than percentage", 0.5f, 0.1f, Location.BTA.name),
        ("Should throttle to fallback when random number is equal than percentage", 0.5f, 0.5f, Location.BTA.name),
        ("Should not throttle to fallback when random number is equal than percentage", 0.5f, 0.7f, locationName)
      )

      forAll(scenarios) {(scenario: String, percentageBeToThrottled: Float, randomNumber: Float, expectedLocation: String) =>

        Helpers.running(FakeApplication(additionalConfiguration = createConfiguration(locationName = locationName, percentageBeToThrottled = percentageBeToThrottled, fallbackLocation = expectedLocation))) {
          //given
          val randomMock = mock[Random]
          when(randomMock.nextFloat()) thenReturn randomNumber

          //and
          val throttlingServiceTest = new ThrottlingService {
            override def random: Random = randomMock
          }

          //when
          val returnedLocation: LocationType = throttlingServiceTest.throttle(initialLocation)

          //then
          returnedLocation.name shouldBe expectedLocation
        }

      }

    }

  }

}
