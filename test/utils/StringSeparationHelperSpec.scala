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

package utils

import support.UnitSpec

class StringSeparationHelperSpec extends UnitSpec {

  import utils.StringSeparationHelper._

  "helper" should {
    "split strings by comma" in {
      ",, ,a,b , c, d ,  e  ,".asCommaSeparatedValues shouldBe Seq("a", "b", "c", "d", "e")
    }

    "split strings by pipe" in {
      "|| |a|b | c| d |  e  |".asPipeSeparatedValues shouldBe Seq("a", "b", "c", "d", "e")
    }
  }
}
