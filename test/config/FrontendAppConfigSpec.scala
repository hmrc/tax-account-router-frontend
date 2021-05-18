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

package config

import org.scalatest.prop.{TableDrivenPropertyChecks, Tables}
import play.api.Configuration
import services.ThrottlingConfig
import support.UnitSpec

class FrontendAppConfigSpec extends UnitSpec {

  "getThrottlingConfig" should {
    "return configuration for throttling location" in {

      val testConfiguration = Map[String, Any](
        "throttling.locations.some-location.percentageToBeThrottled" -> "100",
        "throttling.locations.some-location.fallback" -> "some-fallback"
      )

      val appConfig = new AppConfig {
        override lazy val config: Configuration = Configuration.from(testConfiguration)
      }

      val result = appConfig.getThrottlingConfig("some-location")

      result shouldBe ThrottlingConfig(100, Some("some-fallback"))
    }

    "return default values if configuration absent" in {
      val appConfig = new AppConfig {
        override lazy val config: Configuration = Configuration.empty
      }

      val result = appConfig.getThrottlingConfig("some-location")

      result shouldBe ThrottlingConfig(0, None)
    }
  }

  "businessEnrolments" should {

    val scenarios = Tables.Table[Map[String, Any], Set[String]](
      ("testConfiguration", "expectedEnrolments"),
      (Map.empty, Set.empty[String]),
      (Map("business-enrolments" -> ""), Set.empty[String]),
      (Map("business-enrolments" -> "a"), Set("a")),
      (Map("business-enrolments" -> "a,b,c"), Set("a","b","c")),
      (Map("business-enrolments" -> ",  a ,, b  ,c,"), Set("a","b","c"))
    )

    TableDrivenPropertyChecks.forAll(scenarios) { (testConfiguration, expectedEnrolments) =>

      s"return enrolments for configuration - $testConfiguration" in {
        val appConfig = new AppConfig {
          override lazy val config: Configuration = Configuration.from(testConfiguration)
        }

        val result = appConfig.businessEnrolments

        result shouldBe expectedEnrolments
      }
    }
  }

  "saEnrolments" should {

    val scenarios = Tables.Table[Map[String, Any], Set[String]](
      ("testConfiguration", "expectedEnrolments"),
      (Map.empty, Set.empty[String]),
      (Map("self-assessment-enrolments" -> ""), Set.empty[String]),
      (Map("self-assessment-enrolments" -> "a"), Set("a")),
      (Map("self-assessment-enrolments" -> "a,b,c"), Set("a","b","c")),
      (Map("self-assessment-enrolments" -> ",  a ,, b  ,c,"), Set("a","b","c"))
    )

    TableDrivenPropertyChecks.forAll(scenarios) { (testConfiguration, expectedEnrolments) =>

      s"return enrolments for configuration - $testConfiguration" in {
        val appConfig = new AppConfig {
          override lazy val config: Configuration = Configuration.from(testConfiguration)
        }

        val result = appConfig.saEnrolments

        result shouldBe expectedEnrolments
      }
    }
  }

  "report a problem urls" in {
    val appConfig = new AppConfig {
      override lazy val config: Configuration = Configuration.empty
    }


    appConfig.reportAProblemPartialUrl == "/contact/problem_reports_ajax?service=tax-account-router-frontend"
    appConfig.reportAProblemNonJSUrl == "/contact/problem_reports_nonjs?service=tax-account-router-frontend"
  }
}
