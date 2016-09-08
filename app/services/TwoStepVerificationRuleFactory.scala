package services

import model.Locations.locationFromConf
import model.{ConfiguredEnrolmentCategory, EnrolmentCategory, Location}
import play.api.Play.{configuration, current}

import scala.collection.JavaConversions._

case class ThrottleLocations(optional: Location, mandatory: Location)

case class TwoStepVerificationRule(name: String, enrolmentCategories: Set[EnrolmentCategory], adminLocations: ThrottleLocations, assistantLocations: ThrottleLocations)

trait TwoStepVerificationRuleFactory {

  lazy val rules = configuration.getConfig("two-step-verification-rules").fold(List.empty[TwoStepVerificationRule]) { rules =>
    rules.subKeys.toList.map { ruleName =>
      val ruleLocation = location(ruleName) _
      TwoStepVerificationRule(ruleName, enrolments(ruleName), ruleLocation("admin"), ruleLocation("assistant"))
    }
  }

  private def enrolments(ruleName: String) =
    configuration.getStringList(s"two-step-verification-rules.$ruleName.enrolments").fold(Set.empty[EnrolmentCategory])(_.toSet.map(ConfiguredEnrolmentCategory))

  private def location(ruleName: String)(roleIdentifier: String) = {
    def locationConfigurationKey(throttleIdentifier: String) = configuration.getString(s"two-step-verification-rules.$ruleName.$roleIdentifier.$throttleIdentifier").getOrElse(throw new RuntimeException(s"location not defined for 2sv rule - $ruleName - $roleIdentifier - $throttleIdentifier"))
    def aLocation(throttleIdentifier: String) = {
      locationFromConf(locationConfigurationKey(throttleIdentifier).split("""\.""")(1))
    }
    ThrottleLocations(aLocation("optional"), aLocation("mandatory"))
  }


}
