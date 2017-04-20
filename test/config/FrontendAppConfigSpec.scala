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

package config

import org.scalatest.prop.{TableDrivenPropertyChecks, Tables}
import play.api.Configuration
import uk.gov.hmrc.play.test.UnitSpec

class FrontendAppConfigSpec extends UnitSpec {

  "getThrottlingConfig" should {
    "return configuration for throttling location" in {

      val testConfiguration = Map[String, Any](
        "throttling.locations.some-location.key1" -> "value1",
        "throttling.locations.some-location.key2" -> "value2"
      )

      val appConfig = new AppConfig {
        override lazy val config = Configuration.from(testConfiguration)
      }

      val result = appConfig.getThrottlingConfig("some-location")

      val expectedConfiguration = Configuration.from(Map[String, Any](
        "key1" -> "value1",
        "key2" -> "value2"
      ))

      result shouldBe expectedConfiguration
    }
  }

  "businessEnrolments" should {

    val scenarios = Tables.Table[String, Set[String]](
      ("businessEnrolments", "expectedEnrolments"),
      ("a", Set("a")),
      ("a,b,c", Set("a","b","c")),
      (",  a ,, b  ,c,", Set("a","b","c"))
    )

    TableDrivenPropertyChecks.forAll(scenarios) { (businessEnrolments, expectedEnrolments) =>

      s"return enrolments for configuration - $businessEnrolments" in {
        val testConfiguration = Map[String, Any](
          "business-enrolments" -> businessEnrolments
        )

        val appConfig = new AppConfig {
          override lazy val config = Configuration.from(testConfiguration)
        }

        val result = appConfig.businessEnrolments

        result shouldBe expectedEnrolments
      }
    }
  }
}
