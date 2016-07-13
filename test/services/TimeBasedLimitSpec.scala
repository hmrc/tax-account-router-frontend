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

import org.joda.time.DateTime
import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec

class TimeBasedLimitSpec extends UnitSpec {

  "TimeBasedLimit" should {
    "return the hourly limit in the configuration for the current hour if defined" in new Setup {
      val expectedLimit = 10
      running(FakeApplication(additionalConfiguration = Map("two-step-verification.throttle.12" -> expectedLimit))) {
        timeBasedLimit().getCurrentPercentageLimit shouldBe expectedLimit
      }
    }
    "return the 'default' limit in configuration if hourly limit is not defined for current hour" in new Setup {
      val expectedLimit = 15
      running(FakeApplication(additionalConfiguration = Map(
        "two-step-verification.throttle.12" -> null,
        "two-step-verification.throttle.default" -> expectedLimit
      ))) {
        timeBasedLimit().getCurrentPercentageLimit shouldBe expectedLimit
      }
    }
    "return a value lower than zero if both hourly limit for current hour and 'default' limit are not defined in configuration" in new Setup {
      running(FakeApplication(additionalConfiguration = Map("two-step-verification.throttle" -> null))) {
        timeBasedLimit().getCurrentPercentageLimit should be < 0.0
      }
    }
  }

  sealed trait Setup {
    val fixedDateTime = DateTime.parse("2016-07-07T12:30:00Z")

    def timeBasedLimit = () => new TimeBasedLimit {
      override def dateTimeProvider: () => DateTime = () => fixedDateTime
    }
  }
}
