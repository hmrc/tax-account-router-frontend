package services

import model.Location
import play.api.Play.{configuration, current}

import scala.collection.JavaConversions._

case class ThrottleLocations(optional: Location, mandatory: Location)

case class TwoStepVerificationRule(name: String, enrolments: Set[Set[String]], adminLocations: ThrottleLocations, assistantLocations: ThrottleLocations)

trait TwoStepVerificationRuleFactory {

  def rules = configuration.getConfig("two-step-verification-rules").fold(List.empty[TwoStepVerificationRule]) { rules =>
    rules.subKeys.toList.map { ruleName =>
      val ruleLocation = location(ruleName) _
      TwoStepVerificationRule(ruleName, enrolments(ruleName), ruleLocation("admin"), ruleLocation("assistant"))
    }
  }

  private def enrolments(ruleName: String) = configuration.getList(s"two-step-verification-rules.$ruleName.enrolments").fold(Set.empty[Set[String]]) { confs =>
    confs.map { conf =>
      conf.unwrapped().asInstanceOf[java.util.List[String]].toSet[String]
    }.toSet[Set[String]]
  }

  private def location(ruleName: String)(roleIdentifier: String) = {
    def locationConfigurationKey(throttleIdentifier: String) = configuration.getString(s"two-step-verification-rules.$ruleName.$roleIdentifier.$throttleIdentifier").getOrElse(throw new RuntimeException(s"location not defined for 2sv rule - $ruleName - $roleIdentifier - $throttleIdentifier"))

    def aLocation(throttleIdentifier: String) = {
      val locationName = locationConfigurationKey(throttleIdentifier)
      configuration.getConfig(locationName).map { conf =>
        val name = conf.getString("name").getOrElse(throw new RuntimeException(s"name not configured for location - $locationName"))
        val url = conf.getString("url").getOrElse(throw new RuntimeException(s"url not configured for location - $locationName"))
        val queryParams = conf.getConfig("queryparams").map { queryParamsConf =>
          queryParamsConf.entrySet.foldLeft(Map.empty[String, String]) {
            case (result, (key, value)) => result ++ Map(key -> value.unwrapped().asInstanceOf[String])
          }
        }.getOrElse(Map.empty[String, String])
        Location(name, url, queryParams)
      }.getOrElse(throw new RuntimeException(s"location configuration not defined for $locationName"))
    }
    ThrottleLocations(aLocation("optional"), aLocation("mandatory"))
  }


}
