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

import config.AppConfig
import org.mockito.Mockito._
import model.{Location, Locations}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Configuration
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec

class LocationConfigurationFactorySpec extends UnitSpec with MockitoSugar with OneAppPerSuite {

  "configurationForLocation" should {
    "retrieve appropriate configuration" in new Setup {
      implicit val fakeRequest = FakeRequest()

      when(mockAppConfig.getThrottlingConfig("name1")).thenReturn(testConfiguration)

      val location = Location("name1", "some-url")

      val result:Configuration = locationConfigurationFactory.configurationForLocation(location, fakeRequest)

      result shouldBe testConfiguration
    }

    "retrieve appropriate configuration for PTA" in new Setup {
      implicit val fakeRequest = FakeRequest()

      when(mockAppConfig.getThrottlingConfig("personal-tax-account-verify")).thenReturn(testConfiguration)

      val location = Locations.PersonalTaxAccount

      val result:Configuration = locationConfigurationFactory.configurationForLocation(location, fakeRequest)

      result shouldBe testConfiguration
    }

    "retrieve appropriate configuration for PTA with token in session" in new Setup {

      implicit val fakeRequest = FakeRequest().withSession("token" -> "some-token")

      when(mockAppConfig.getThrottlingConfig("personal-tax-account-gg")).thenReturn(testConfiguration)

      val location = Locations.PersonalTaxAccount

      val result:Configuration = locationConfigurationFactory.configurationForLocation(location, fakeRequest)

      result shouldBe testConfiguration
    }
  }

  class Setup {
    val mockAppConfig = mock[AppConfig]

    val testConfiguration = Configuration.from(Map(
      "key1" -> "value1",
      "key2" -> "value2"
    ))

    val locationConfigurationFactory = new LocationConfigurationFactory {
      override val configuration: AppConfig = mockAppConfig
    }
  }
}
