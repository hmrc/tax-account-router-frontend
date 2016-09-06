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
import controllers.ExternalUrls
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

  private case class ThrottleLocations(optional: Location, mandatory: Location)

  private case class Biz2SVRule(name: String, conditions: List[Condition], adminLocations: ThrottleLocations, assistantLocations: ThrottleLocations)

  private val biz2svRules = List(
    Biz2SVRule("sa", List(not(HasRegisteredFor2SV), not(HasStrongCredentials), GGEnrolmentsAvailable, HasOnlyEnrolments(SA)), ThrottleLocations(twoStepVerificationContinueBTA(BusinessTaxAccount), twoStepVerificationContinueBTA(TaxAccountRouterHome)), ThrottleLocations(twoStepVerificationContinueBTA(BusinessTaxAccount), twoStepVerificationContinueBTA(TaxAccountRouterHome))),
    Biz2SVRule("sa_vat", List(not(HasRegisteredFor2SV), not(HasStrongCredentials), GGEnrolmentsAvailable, HasOnlyEnrolments(SA, VAT)), ThrottleLocations(SetUpExtraSecurity, twoStepVerification(AreYouSharing)(TaxAccountRouterHome)), ThrottleLocations(BusinessTaxAccount, BusinessTaxAccount))
  )

  val continueToAccountUrl = s"${ExternalUrls.taxAccountRouterHost}/account"

  def getDestinationVia2SV(continue: Location, ruleContext: RuleContext, auditContext: TAuditContext)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier) = {

    def throttleLocation(rule: Biz2SVRule) = ruleContext.isAdmin.map(if (_) rule.adminLocations else rule.assistantLocations)

    if (twoStepVerificationEnabled && continue == BusinessTaxAccount) {

      val applicableRule = biz2svRules.findOne(_.conditions.forAll(_.evaluate(authContext, ruleContext, auditContext)))

      applicableRule.flatMap {
        case Some(biz2svRule) =>
          twoStepVerificationThrottle.registrationMandatory(biz2svRule.name, authContext.user.oid) match {
            case true =>
              throttleLocation(biz2svRule).map { location =>
                auditContext.setSentToMandatory2SVRegister(biz2svRule.name)
                Some(location.mandatory)
              }
            case _ =>
              throttleLocation(biz2svRule).map { location =>
                auditContext.setSentToOptional2SVRegister(biz2svRule.name)
                Some(location.optional)
              }
          }
        case _ => Future.successful(None)
      }
    } else Future.successful(None)
  }

  private def twoStepVerification(continue: Location)(failure: Location) = Locations.twoStepVerification(Map("continue" -> continue.fullUrl, "failure" -> failure.fullUrl, "origin" -> "business-tax-account"))

  private def twoStepVerificationContinueBTA = twoStepVerification(BusinessTaxAccount) _


}

object TwoStepVerification extends TwoStepVerification with AppConfigHelpers {

  override lazy val twoStepVerificationHost = getConfigurationString("two-step-verification.host")

  override lazy val twoStepVerificationPath = getConfigurationString("two-step-verification.path")

  override lazy val twoStepVerificationEnabled = getConfigurationBoolean("two-step-verification.enabled")

  override lazy val twoStepVerificationThrottle = TwoStepVerificationThrottle
}
