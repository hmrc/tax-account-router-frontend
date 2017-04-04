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

package engine

import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import uk.gov.hmrc.play.test.UnitSpec

class ThrottlerSpec extends UnitSpec with Matchers {

  "shouldThrottle" should {

    // expected user modulus is always 50
    val discriminator = "f3c48669-ecd2-41a4-ac51-aa6b9898b462" // Math.abs("82be396f-81c1-4d72-bdf6-b924e0c75ec0".md5.hashCode % 100) = 50

    val scenarios = Table[String, Int, Boolean](
      ("scenario", "expectedThreshold", "expectedResult"),
      ("return false when threshold is same as user modulus", 50, true),
      ("return false when user modulus is lower than threshold", 51, true),
      ("return true when user modulus is greater than threshold", 49, false)
    )

    forAll(scenarios) { (scenario, expectedThreshold, expectedResult) =>
      scenario in {
        Throttler.shouldThrottle(discriminator, expectedThreshold) shouldBe expectedResult
      }
    }
  }
}
