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

package model

import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import support.UnitSpec

class LocationsSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  "locations" should {

    "return Location when config has no missing keys" in {
      val result = Locations.PersonalTaxAccount
      result shouldBe Location("personal-tax-account", "http://localhost:9232/personal-account")
    }

    "return Location when config has no missing url key" in {

      val configuration: Map[String, Any] = Map[String, Any](
        "locations.tax-account-router.name" -> "some-name"
      )

      val caught = intercept[RuntimeException] {
        new Locations {
          lazy override val config: Configuration =
            GuiceApplicationBuilder(loadConfiguration = env => Configuration.load(env)).configure(configuration).configuration
        }.TaxAccountRouterHome
      }

      caught shouldNot be(null)
      caught.getMessage shouldBe "key 'url' not configured for location 'tax-account-router'"
    }

    "return Location when config has no missing name key" in {

      val configuration: Map[String, Any] = Map[String, Any]()

      val caught = intercept[RuntimeException] {
        new Locations {
          lazy override val config: Configuration =
            GuiceApplicationBuilder(loadConfiguration = env => Configuration.load(env)).configure(configuration).configuration
        }.BusinessTaxAccount
      }

      caught shouldNot be(null)
      caught.getMessage shouldBe "key 'name' not configured for location 'bta'"
    }
  }
}
