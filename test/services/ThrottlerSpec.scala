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

import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import uk.gov.hmrc.play.test.UnitSpec

class ThrottlerSpec extends UnitSpec with Matchers {

  "shouldThrottle" should {

    // expected user modulus is always 50
    val discriminator = "f3c48669-ecd2-41a4-ac51-aa6b9898b462" // Math.abs("f3c48669-ecd2-41a4-ac51-aa6b9898b462".md5.hashCode % 100) = 50

    val scenarios = Table[String, Int, Boolean](
      ("scenario", "percentageToBeThrottled", "expectedResult"),

      ("threshold is lower than user modulus", 49, false),
      ("threshold is same as user modulus", 50, false),
      ("threshold is greater than user modulus", 51, true)
    )

    forAll(scenarios) { (scenario, percentageToBeThrottled, expectedResult) =>
      s"return $expectedResult in scenario: $scenario" in {
        Throttler.shouldThrottle(discriminator, percentageToBeThrottled) shouldBe expectedResult
      }
    }
  }

  it should {

    val discriminator99 = "c2103cec-03f5-4605-b980-dd2d309d48e9" // Math.abs("c2103cec-03f5-4605-b980-dd2d309d48e9".md5.hashCode % 100) = 99
    val discriminator0 = "4c72d67c-df80-4967-953d-c64ffc22d720" // Math.abs("4c72d67c-df80-4967-953d-c64ffc22d720".md5.hashCode % 100) = 0

    val scenarios = Table[String, String, Int, Boolean](
      ("scenario", "discriminator", "percentageToBeThrottled", "expectedResult"),

      ("threshold is same as user modulus of 0", discriminator0, 0, false),
      ("threshold is higher than user modulus of 0", discriminator0, 1, true),

      ("threshold is same as user modulus of 99", discriminator99, 99, false),
      ("threshold is higher than user modulus of 99", discriminator99, 100, true)
    )

    forAll(scenarios) { (scenario, discriminator, percentageToBeThrottled, expectedResult) =>
      s"return $expectedResult in scenario: $scenario" in {
        Throttler.shouldThrottle(discriminator, percentageToBeThrottled) shouldBe expectedResult
      }
    }
  }
}
