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

import model.{Location, SA, VAT}
import play.api.test.FakeApplication
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class TwoStepVerificationUserSegmentsSpec extends UnitSpec with WithFakeApplication {

  override lazy val fakeApplication = FakeApplication()

  "userSegments" should {

    "have correct names" in new Setup {
      val names = segments.map(_.name)
      names should contain("sa")
      names should contain("sa_vat")
    }

    "have correct enrolments" in new Setup {
      val enrolments = segments.map(rule => (rule.name, rule.enrolmentCategories))
      enrolments should contain(("sa", Set(SA)))
      enrolments should contain(("sa_vat", Set(SA, VAT)))
    }

    "have correct admin optional locations" in new Setup {
      val locations = segments.map(rule => (rule.name, rule.adminLocations.optional))

      locations should contain(("sa", Location("two-step-verification", "http://localhost:9025/coafe/two-step-verification/register", Map("continue" -> "http://localhost:9020/business-account", "failure" -> "http://localhost:9020/business-account", "origin" -> "business-tax-account"))))
      locations should contain(("sa_vat", Location("set-up-extra-security", "http://localhost:9851/user-delegation/set-up-extra-security")))
    }

    "have correct admin mandatory locations" in new Setup {
      val locations = segments.map(rule => (rule.name, rule.adminLocations.mandatory))

      locations should contain(("sa", Location("two-step-verification", "http://localhost:9025/coafe/two-step-verification/register", Map("continue" -> "http://localhost:9020/business-account", "failure" -> "http://localhost:9280/account", "origin" -> "business-tax-account"))))
      locations should contain(("sa_vat", Location("set-up-extra-security", "http://localhost:9851/user-delegation/set-up-extra-security")))
    }

    "have correct assistant optional locations" in new Setup {
      val locations = segments.map(rule => (rule.name, rule.assistantLocations.optional))

      locations should contain(("sa", Location("two-step-verification", "http://localhost:9025/coafe/two-step-verification/register", Map("continue" -> "http://localhost:9020/business-account", "failure" -> "http://localhost:9020/business-account", "origin" -> "business-tax-account"))))
      locations should contain(("sa_vat", Location("business-tax-account", "http://localhost:9020/business-account")))
    }

    "have correct assistant mandatory locations" in new Setup {
      val locations = segments.map(rule => (rule.name, rule.assistantLocations.mandatory))

      locations should contain(("sa", Location("two-step-verification", "http://localhost:9025/coafe/two-step-verification/register", Map("continue" -> "http://localhost:9020/business-account", "failure" -> "http://localhost:9280/account", "origin" -> "business-tax-account"))))
      locations should contain(("sa_vat", Location("business-tax-account", "http://localhost:9020/business-account", Map())))
    }

  }

  trait Setup {
    val saEnrolments = Set("enr3", "enr4")
    val vatEnrolments = Set("enr5", "enr6")
    val segments = new TwoStepVerificationUserSegments {}.segments
  }

}
