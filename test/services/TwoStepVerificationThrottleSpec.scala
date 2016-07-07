/*
 * Copyright 2016 HM Revenue & Customs
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

import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import uk.gov.hmrc.play.test.UnitSpec

class TwoStepVerificationThrottleSpec extends UnitSpec with MockitoSugar {

  "registrationMandatory" should {

    // expected user modulus is always -50
    val discriminator = "2d5577e9-cc54-4faa-9f88-a60ac56bac1e" // Math.abs("2d5577e9-cc54-4faa-9f88-a60ac56bac1e".hashCode % 100) = 50

    val scenarios = Table(
      ("scenario", "expectedThreshold", "expectedResult"),
      ("return true when threshold is same as user modulus", 50, true),
      ("return true when user modulus is lower than threshold", 51, true),
      ("return false when user modulus is greater than threshold", 49, false)
    )

    forAll(scenarios) { (scenario: String, expectedThreshold: Int, expectedResult: Boolean) =>
      scenario in new Setup {
        when(hourlyLimitMock.getCurrentLimit).thenReturn(expectedThreshold)

        twoStepVerificationThrottle.registrationMandatory(discriminator) shouldBe expectedResult

        verify(hourlyLimitMock).getCurrentLimit
        verifyNoMoreInteractions(allMocks: _*)
      }
    }
  }

  sealed trait Setup {
    val hourlyLimitMock = mock[HourlyLimit]
    val allMocks = Seq(hourlyLimitMock)

    val twoStepVerificationThrottle = new TwoStepVerificationThrottle {
      override lazy val hourlyLimit = hourlyLimitMock
    }
  }

}
