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

package controllers

import auth.RouterAuthenticationProvider
import config.FrontendAuditConnector
import connector.FrontendAuthConnector
import controllers.UserType.UserType
import engine.{Condition, RuleEngine}
import model.Locations._
import model._
import play.api.Logger
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc._
import services._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier
import utils.EnumJson._

import scala.concurrent.Future

object UserType extends Enumeration {
  type UserType = Value
  val Individual, Business = Value
}

case class UserTypeResponse(userType: UserType)

object UserTypeResponse {
  implicit val userTypeReads = enumFormat(UserType)
  implicit val writes: Writes[UserTypeResponse] = Json.writes[UserTypeResponse]
  implicit val reads: Reads[UserTypeResponse] = Json.reads[UserTypeResponse]
}

object RouterController extends RouterController {
  override protected def authConnector = FrontendAuthConnector

  override val defaultLocation = BusinessTaxAccount

  override val metricsMonitoringService = MetricsMonitoringService

  override val ruleEngine = TarRules

  override val throttlingService = ThrottlingService

  override val twoStepVerification = TwoStepVerification

  override val auditConnector = FrontendAuditConnector

  override def createAuditContext() = AuditContext()

  override val analyticsEventSender = AnalyticsEventSender
}

trait RouterController extends FrontendController with Actions {

  val metricsMonitoringService: MetricsMonitoringService

  def defaultLocation: Location

  def ruleEngine: RuleEngine

  def throttlingService: ThrottlingService

  def twoStepVerification: TwoStepVerification

  def auditConnector: AuditConnector

  def createAuditContext(): TAuditContext

  def analyticsEventSender: AnalyticsEventSender

  val account = AuthenticatedBy(authenticationProvider = RouterAuthenticationProvider, pageVisibility = AllowAll).async { implicit user => request => route(user, request) }

  def route(implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {

    // TODO: where is the credId?
    val ruleContext = RuleContext(None)
    val auditContext = createAuditContext()

    calculateFinalDestination(ruleContext, auditContext).map(location => Redirect(location.fullUrl))
  }

  def calculateUserType(credId: String) = Action.async { implicit request =>
    val ruleContext = RuleContext(Some(credId))
    // TODO: no op audit context!
    val auditContext = createAuditContext()
    calculateFinalDestination(ruleContext, auditContext) map {
      case Locations.PersonalTaxAccount => Ok(Json.toJson(UserTypeResponse(UserType.Individual)))
      case Locations.BusinessTaxAccount => Ok(Json.toJson(UserTypeResponse(UserType.Business)))
    }
  }


  def calculateFinalDestination(ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent]) = {
    val ruleEngineResult = ruleEngine.getLocation(ruleContext, auditContext).map(nextLocation => nextLocation.getOrElse(defaultLocation))

    for {
      destinationAfterRulesApplied <- ruleEngineResult
      destinationAfterThrottleApplied <- throttlingService.throttle(destinationAfterRulesApplied, auditContext, ruleContext)
      finalDestination <- twoStepVerification.getDestinationVia2SV(destinationAfterThrottleApplied, ruleContext, auditContext).map(_.getOrElse(destinationAfterThrottleApplied))
    } yield {
      //sendAuditEvent(auditContext, destinationAfterThrottleApplied)
      metricsMonitoringService.sendMonitoringEvents(auditContext, destinationAfterThrottleApplied)
      Logger.debug(s"routing to: ${finalDestination.name}")
      analyticsEventSender.sendEvents(finalDestination.name, auditContext)
      finalDestination
    }
  }

  private def sendAuditEvent(auditContext: TAuditContext, throttledLocation: Location)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier) = {
    auditContext.toAuditEvent(throttledLocation).foreach { auditEvent =>
      auditConnector.sendEvent(auditEvent)
      Logger.debug(s"Routing decision summary: ${auditEvent.detail \ "reasons"}")
    }
  }
}

object TarRules extends RuleEngine {

  import Condition._

  override val rules = List(
    when(LoggedInViaVerify and not(ArrivingWithCredId)) thenGoTo PersonalTaxAccount withName "pta-home-page-for-verify-user",

    when(LoggedInViaGovernmentGateway or ArrivingWithCredId and not(GGEnrolmentsAvailable)) thenGoTo BusinessTaxAccount withName "bta-home-page-gg-unavailable",

    when(LoggedInViaGovernmentGateway or ArrivingWithCredId and HasAnyBusinessEnrolment) thenGoTo BusinessTaxAccount withName "bta-home-page-for-user-with-business-enrolments",

    when(LoggedInViaGovernmentGateway or ArrivingWithCredId and HasEnrolments(SA) and not(SAReturnAvailable)) thenGoTo BusinessTaxAccount withName "bta-home-page-sa-unavailable",

    when(LoggedInViaGovernmentGateway or ArrivingWithCredId and HasEnrolments(SA) and not(HasPreviousReturns)) thenGoTo BusinessTaxAccount withName "bta-home-page-for-user-with-no-previous-return",

    when(LoggedInViaGovernmentGateway or ArrivingWithCredId and HasEnrolments(SA) and (IsInAPartnership or IsSelfEmployed)) thenGoTo BusinessTaxAccount withName "bta-home-page-for-user-with-partnership-or-self-employment",

    when(LoggedInViaGovernmentGateway or ArrivingWithCredId and HasEnrolments(SA) and not(IsInAPartnership) and not(IsSelfEmployed) and not(HasNino)) thenGoTo BusinessTaxAccount withName "bta-home-page-for-user-with-no-partnership-and-no-self-employment-and-no-nino",

    when(LoggedInViaGovernmentGateway or ArrivingWithCredId and HasEnrolments(SA) and not(IsInAPartnership) and not(IsSelfEmployed)) thenGoTo PersonalTaxAccount withName "pta-home-page-for-user-with-no-partnership-and-no-self-employment",

    when(not(HasAnyInactiveEnrolment) and not(AffinityGroupAvailable)) thenGoTo BusinessTaxAccount withName "bta-home-page-affinity-group-unavailable",

    when(not(HasAnyInactiveEnrolment) and HasIndividualAffinityGroup) thenGoTo PersonalTaxAccount withName "pta-home-page-individual",

    when(AnyOtherRuleApplied) thenGoTo BusinessTaxAccount withName "bta-home-page-passed-through"
  )
}