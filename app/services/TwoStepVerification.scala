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

import config.AppConfigHelpers
import engine.Condition
import engine.Condition._
import model.Locations._
import model._
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

trait TwoStepVerification {

  def twoStepVerificationHost: String

  def twoStepVerificationPath: String

  def twoStepVerificationEnabled: Boolean

  def twoStepVerificationThrottle: TwoStepVerificationThrottle

  def upliftLocationsConfiguration: Option[String]

  def stringToLocation: String => Location

  def biz2svRules: List[Biz2SVRule]

  lazy val upliftLocations = upliftLocationsConfiguration.map(s => s.split(",").map(_.trim).map(stringToLocation).toSet).getOrElse(Set.empty)

  def isUplifted(location: Location) = upliftLocations.contains(location)

  def getDestinationVia2SV(continue: Location, ruleContext: RuleContext, auditContext: TAuditContext)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier) = {

    def throttleLocation(rule: Biz2SVRule) = ruleContext.isAdmin.map(if (_) rule.adminLocations else rule.assistantLocations)

    if (twoStepVerificationEnabled && continue == BusinessTaxAccount) {

      val applicableRule = biz2svRules.findOne(_.conditions.forAll(_.evaluate(authContext, ruleContext, auditContext)))

      applicableRule.flatMap {
        case Some(biz2svRule) =>
          throttleLocation(biz2svRule).map { location =>
            Some(twoStepVerificationThrottle.isRegistrationMandatory(biz2svRule.name, authContext.user.oid) match {
              case true =>
                if (isUplifted(location.mandatory)) auditContext.setSentToMandatory2SVRegister(biz2svRule.name)
                location.mandatory
              case _ =>
                if(isUplifted(location.optional)) auditContext.setSentToOptional2SVRegister(biz2svRule.name)
                location.optional
            })
          }
        case _ => Future.successful(None)
      }
    } else Future.successful(None)
  }

}

object TwoStepVerification extends TwoStepVerification with AppConfigHelpers {

  import play.api.Play.current

  override lazy val twoStepVerificationHost = getConfigurationString("two-step-verification.host")

  override lazy val twoStepVerificationPath = getConfigurationString("two-step-verification.path")

  override lazy val twoStepVerificationEnabled = getConfigurationBoolean("two-step-verification.enabled")

  override lazy val twoStepVerificationThrottle = TwoStepVerificationThrottle

  override lazy val upliftLocationsConfiguration = getConfigurationStringOption("two-step-verification.uplift-locations")

  override lazy val stringToLocation = Locations.locationFromConf _

  override lazy val biz2svRules = TwoStepVerificationUserSegments.biz2svRules
}
