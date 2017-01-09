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

import uk.gov.hmrc.play.test.UnitSpec

class LocationSpec extends UnitSpec {

  "location" should {

    "return a url without query string when no query params are provided" in {
      Location("test-tax-account", "/some-url").fullUrl shouldBe "/some-url"
    }

    "return a url with an encoded query string when query params are provided" in {
      Location("test-tax-account", "/some-url", Map("a" -> "/b", "c" -> "/d")).fullUrl shouldBe "/some-url?a=%2Fb&c=%2Fd"
    }
  }
}
