/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.Configuration
import support.UnitSpec

class FrontendAppConfigSpec extends UnitSpec {

  "config" should {
    "have correct 'report a problem' urls" in {
      val appConfig = new AppConfig {
        override lazy val config: Configuration = Configuration.empty
      }

      appConfig.reportAProblemPartialUrl == "/contact/problem_reports_ajax?service=tax-account-router-frontend"
      appConfig.reportAProblemNonJSUrl == "/contact/problem_reports_nonjs?service=tax-account-router-frontend"
    }
  }
}
