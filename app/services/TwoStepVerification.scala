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

import config.{AppConfigHelpers, FrontendAuditConnector}
import model.Locations._
import model._
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.config.AppName
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

  def auditConnector: AuditConnector

  lazy val upliftLocations = upliftLocationsConfiguration.map(s => s.split(",").map(_.trim).map(stringToLocation).toSet).getOrElse(Set.empty)

  def isUplifted(location: Location) = upliftLocations.contains(location)

  private def sendAuditEvent(biz2SVRule: Biz2SVRule, ruleContext: RuleContext, mandatory: Boolean)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier) = {
    val enrolmentsFut = ruleContext.activeEnrolments
    val userDetailsFut = ruleContext.userDetails
    val transactionName = if (mandatory) "two step verification mandatory" else "two step verification optional"
    for {
      enrolments <- enrolmentsFut
      userDetails <- userDetailsFut
    } yield {
      val auditEvent = ExtendedDataEvent(
        auditSource = AppName.appName,
        auditType = "TwoStepVerificationOutcome",
        tags = hc.toAuditTags(transactionName, request.path),
        detail = Json.obj(
          "ruleApplied" -> s"rule_${biz2SVRule.name}",
          "credentialRole" -> userDetails.credentialRole.map(_.value),
          "userEnrolments" -> Json.toJson(enrolments),
          "mandatory" -> mandatory.toString()
        )
      )
      auditConnector.sendEvent(auditEvent)
    }
  }

  def getDestinationVia2SV(continue: Location, ruleContext: RuleContext, auditContext: TAuditContext)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier) = {

    def throttleLocations(biz2SVRule: Option[Biz2SVRule]) = biz2SVRule match {
      case Some(rule) => ruleContext.isAdmin.map(if (_) rule.adminLocations else rule.assistantLocations).map(Some(_))
      case None => Future.successful(None)
    }

    def throttleLocation(rule: Biz2SVRule, locations: ThrottleLocations, credId: String) = twoStepVerificationThrottle.isRegistrationMandatory(rule.name, credId) match {
      case true =>
        if (isUplifted(locations.mandatory)) {
          auditContext.setSentToMandatory2SVRegister(rule.name)
          sendAuditEvent(rule, ruleContext, mandatory = true)
        }
        locations.mandatory
      case _ =>
        if (isUplifted(locations.optional)) {
          auditContext.setSentToOptional2SVRegister(rule.name)
          sendAuditEvent(rule, ruleContext, mandatory = false)
        }
        locations.optional
    }

    if (twoStepVerificationEnabled && continue == BusinessTaxAccount) {
      for {
        applicableRule <- biz2svRules.findOne(_.conditions.forAll(_.evaluate(authContext, ruleContext, auditContext)))
        throttleLocations <- throttleLocations(applicableRule)
        credId <- ruleContext.credentialId
      } yield {
        (applicableRule, throttleLocations) match {
          case (Some(rule), Some(locations)) => Some(throttleLocation(rule, locations, credId))
          case _ => None
        }
      }
    }
    else Future.successful(None)
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

  override val auditConnector = FrontendAuditConnector
}
