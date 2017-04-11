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

import model.{Location, Locations}
import org.scalatestplus.play.OneAppPerSuite
import play.api.Configuration
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec

class LocationConfigurationFactorySpec extends UnitSpec with OneAppPerSuite {

  "configurationForLocation" should {
    "retrieve appropriate configuration" in {

      implicit val fakeRequest = FakeRequest()
      val testConfiguration = Configuration.from(Map(
        "throttling.locations.name1.key1" -> "value1",
        "throttling.locations.name1.key2" -> "value2"
      ))

      val location = Location("name1", "url")

      val locationConfigurationFactory = new LocationConfigurationFactory {
        override val configuration: Configuration = testConfiguration
      }

      val result:Configuration = locationConfigurationFactory.configurationForLocation(location, fakeRequest)

      val expectedConfiguration = Configuration.from(Map(
        "key1" -> "value1",
        "key2" -> "value2"
      ))

      result shouldBe expectedConfiguration
    }

    "retrieve appropriate configuration for PTA" in {
      implicit val fakeRequest = FakeRequest()
      val testConfiguration = Configuration.from(Map(
        "throttling.locations.personal-tax-account-verify.key1" -> "value1",
        "throttling.locations.personal-tax-account-verify.key2" -> "value2"
      ))

      val location = Locations.PersonalTaxAccount

      val locationConfigurationFactory = new LocationConfigurationFactory {
        override val configuration: Configuration = testConfiguration
      }

      val result:Configuration = locationConfigurationFactory.configurationForLocation(location, fakeRequest)

      val expectedConfiguration = Configuration.from(Map(
        "key1" -> "value1",
        "key2" -> "value2"
      ))

      result shouldBe expectedConfiguration
    }

    "retrieve appropriate configuration for PTA with token in session" in {

      implicit val fakeRequest = FakeRequest().withSession("token" -> "some-token")
      val testConfiguration = Configuration.from(Map(
        "throttling.locations.personal-tax-account-gg.key1" -> "value1",
        "throttling.locations.personal-tax-account-gg.key2" -> "value2"
      ))

      val location = Locations.PersonalTaxAccount

      val locationConfigurationFactory = new LocationConfigurationFactory {
        override val configuration: Configuration = testConfiguration
      }

      val result:Configuration = locationConfigurationFactory.configurationForLocation(location, fakeRequest)

      val expectedConfiguration = Configuration.from(Map(
        "key1" -> "value1",
        "key2" -> "value2"
      ))

      result shouldBe expectedConfiguration
    }
  }
}
