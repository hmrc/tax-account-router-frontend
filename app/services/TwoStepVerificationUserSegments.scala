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

import model.Locations.locationFromConf
import model.{ConfiguredEnrolmentCategory, EnrolmentCategory, Location}
import play.api.Play.{configuration, current}

import scala.collection.JavaConversions._

case class ThrottleLocations(optional: Location, mandatory: Location)

case class TwoStepVerificationUserSegment(name: String, enrolmentCategories: Set[EnrolmentCategory], adminLocations: ThrottleLocations, assistantLocations: ThrottleLocations)

trait TwoStepVerificationUserSegments {

  private val adminConfigPath = "admin"
  private val assistantConfigPath = "assistant"
  private val optionalConfigPath = "optional"
  private val mandatoryConfigPath = "mandatory"

  private val twoStepVerificationRulesConfigName = "two-step-verification.user-segment"

  lazy val segments = configuration.getConfig(twoStepVerificationRulesConfigName).fold(List.empty[TwoStepVerificationUserSegment]) { rules =>
    rules.subKeys.toList.map { ruleName =>
      val ruleLocation = location(ruleName) _

      TwoStepVerificationUserSegment(ruleName, enrolments(ruleName), ruleLocation(adminConfigPath), ruleLocation(assistantConfigPath))
    }
  }

  private def enrolments(ruleName: String) =
    configuration.getStringList(s"$twoStepVerificationRulesConfigName.$ruleName.enrolments").fold(Set.empty[EnrolmentCategory])(_.toSet.map(ConfiguredEnrolmentCategory))

  private def location(ruleName: String)(roleIdentifier: String) = {
    def locationConfigurationKey(twoStepVerificationMode: String) = configuration.getString(s"$twoStepVerificationRulesConfigName.$ruleName.$roleIdentifier.$twoStepVerificationMode").getOrElse(throw new RuntimeException(s"location not defined for 2sv rule - $ruleName - $roleIdentifier - $twoStepVerificationMode"))
    def aLocation(twoStepVerificationMode: String) = {
      locationFromConf(locationConfigurationKey(twoStepVerificationMode).split("""\.""")(1))
    }
    ThrottleLocations(aLocation(optionalConfigPath), aLocation(mandatoryConfigPath))
  }


}