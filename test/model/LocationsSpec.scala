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

import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec

class LocationsSpec extends UnitSpec {

  "Locations" should {
    "return a Location from the configuration" in new Setup {

      implicit val fakeApplication = FakeApplication(additionalConfiguration = additionalConfiguration)

      running(fakeApplication) {
        val locations = new Locations {}

        val location = locations.locationFromConf("some-location")

        val expectedLocation = Location(locationName, locationUrl, Map("continue" -> continueQueryParam, "failure" -> failureQueryParam, "origin" -> originQueryParam))

        location shouldBe expectedLocation
      }
    }

    "throw RuntimeException when expected location configuration element is not existing" in {

      implicit val application = FakeApplication()

      running(application) {
        val locations = new Locations {}

        val thrown = intercept[RuntimeException] {
          locations.locationFromConf("some-location")
        }

        thrown.getMessage shouldBe "location configuration not defined for some-location"
      }
    }

    "throw RuntimeException when location name is missing from configuration" in new Setup {

      implicit val fakeApplication = FakeApplication(additionalConfiguration = additionalConfiguration - "locations.some-location.name")

      running(fakeApplication) {
        val locations = new Locations {}

        val thrown = intercept[RuntimeException] {
          locations.locationFromConf("some-location")
        }

        thrown.getMessage shouldBe "name not configured for location - some-location"
      }
    }

    "throw RuntimeException when location url is missing from configuration" in new Setup {

      implicit val fakeApplication = FakeApplication(additionalConfiguration = additionalConfiguration - "locations.some-location.url")

      running(fakeApplication) {
        val locations = new Locations {}

        val thrown = intercept[RuntimeException] {
          locations.locationFromConf("some-location")
        }

        thrown.getMessage shouldBe "url not configured for location - some-location"
      }
    }

    trait Setup {
      val locationName = "some-name"
      val locationUrl = "some-url"
      val continueQueryParam = "continue-url"
      val failureQueryParam = "failure-url"
      val originQueryParam = "origin-url"

      val additionalConfiguration = Map(
        "locations.some-location.name" -> locationName,
        "locations.some-location.url" -> locationUrl,
        "locations.some-location.queryparams.continue" -> continueQueryParam,
        "locations.some-location.queryparams.failure" -> failureQueryParam,
        "locations.some-location.queryparams.origin" -> originQueryParam
      )
    }
  }
}
