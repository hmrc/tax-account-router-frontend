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

package model

import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}

class LocationSpec extends UnitSpec with WithFakeApplication {

  "A location" should {
    "have a default value" in {
      Location("test-tax-account", "/test-account", LocationGroup.PTA).url shouldBe "/test-account"
    }

    "can be overridden with a different host" in running(FakeApplication(additionalConfiguration = Map("test-tax-account.host" -> "localhost:9000"))) {

      Location("test-tax-account", "/test-account", LocationGroup.PTA).url shouldBe "localhost:9000/test-account"
    }

    "can be overridden with a different path" in running(FakeApplication(additionalConfiguration = Map("test-tax-account.path" -> "/test-account/full"))) {

      Location("test-tax-account", "/test-account", LocationGroup.PTA).url shouldBe "/test-account/full"
    }
  }

}
