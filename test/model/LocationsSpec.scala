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

package model

import config.AppConfig
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

class LocationsSpec extends UnitSpec with MockitoSugar {

  "locations" should {

    "return Location when config has no missing keys" in {
      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.getLocationConfig("pta", "name")).thenReturn(Some("some-name"))
      when(mockAppConfig.getLocationConfig("pta", "url")).thenReturn(Some("some-url"))

      val locations = new Locations {
        val appConfig = mockAppConfig
      }

      val result = locations.PersonalTaxAccount

      result shouldBe Location("some-name", "some-url")
    }

    "return Location when config has no missing url key" in {
      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.getLocationConfig("tax-account-router", "name")).thenReturn(Some("some-name"))
      when(mockAppConfig.getLocationConfig("tax-account-router", "url")).thenReturn(None)

      val locations = new Locations {
        val appConfig = mockAppConfig
      }

      val caught = intercept[RuntimeException] {
        locations.TaxAccountRouterHome
      }

      caught shouldNot be(null)
      caught.getMessage shouldBe "key 'url' not configured for location 'tax-account-router'"
    }

    "return Location when config has no missing name key" in {
      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.getLocationConfig("bta", "name")).thenReturn(None)

      val locations = new Locations {
        val appConfig = mockAppConfig
      }

      val caught = intercept[RuntimeException] {
        locations.BusinessTaxAccount
      }

      caught shouldNot be(null)
      caught.getMessage shouldBe "key 'name' not configured for location 'bta'"
    }
  }
}
