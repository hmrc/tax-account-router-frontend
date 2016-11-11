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

    // expected user modulus is always 500
    val discriminator = "6e60e239-16e9-4bef-93d6-319b18d23587" // expected hashCode of MD5 value is 500

    val scenarios = Table(
      ("scenario", "expectedThreshold", "expectedResult"),
      ("return true when threshold is same as user modulus", 50.0, true),
      ("return true when user modulus is lower than threshold", 50.1, true),
      ("return false when user modulus is greater than threshold", 49.9, false)
    )

    forAll(scenarios) { (scenario: String, expectedThreshold: Double, expectedResult: Boolean) =>
      scenario in new Setup {
        val ruleName = "some rule"
        when(timeBasedLimitMock.getCurrentPercentageLimit(ruleName)).thenReturn(expectedThreshold)

        twoStepVerificationThrottle.isRegistrationMandatory(ruleName,discriminator) shouldBe expectedResult

        verify(timeBasedLimitMock).getCurrentPercentageLimit(ruleName)
        verifyNoMoreInteractions(timeBasedLimitMock)
      }
    }
  }

  sealed trait Setup {
    val timeBasedLimitMock = mock[TimeBasedLimit]

    val twoStepVerificationThrottle = new TwoStepVerificationThrottle {
      override lazy val timeBasedLimit = timeBasedLimitMock
    }
  }

}
