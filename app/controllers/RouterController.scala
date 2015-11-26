/*
 * Copyright 2015 HM Revenue & Customs
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
import engine.{Condition, Rule, RuleEngine}
import model.Location._
import model._
import play.api.Logger
import play.api.mvc._
import services._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

object RouterController extends RouterController {
  override protected def authConnector: AuthConnector = FrontendAuthConnector

  override val defaultLocation: LocationType = BusinessTaxAccount

  override val metricsMonitoringService: MetricsMonitoringService = MetricsMonitoringService

  override val ruleEngine: RuleEngine = TarRules

  override def throttlingService: ThrottlingService = ThrottlingService

  override def auditConnector: AuditConnector = FrontendAuditConnector

  override def createAuditContext(): TAuditContext = AuditContext()
}

trait RouterController extends FrontendController with Actions {

  val metricsMonitoringService: MetricsMonitoringService

  def defaultLocation: LocationType

  def ruleEngine: RuleEngine

  def throttlingService: ThrottlingService

  def auditConnector: AuditConnector

  def createAuditContext(): TAuditContext

  val account = AuthenticatedBy(authenticationProvider = RouterAuthenticationProvider, pageVisibility = AllowAll).async { implicit user => request => route(user, request) }

  def route(implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {

    val ruleContext = RuleContext(authContext)

    val auditContext: TAuditContext = createAuditContext()

    val nextLocation: Future[Option[LocationType]] = ruleEngine.getLocation(authContext, ruleContext, auditContext)

    nextLocation.flatMap(locationCandidate => {
      val location: LocationType = locationCandidate.getOrElse(defaultLocation)
      val eventualLocationType: Future[LocationType] = throttlingService.throttle(location, auditContext)
      eventualLocationType.map { throttledLocation =>
        Logger.debug(s"routing to: ${throttledLocation.name}")
        sendAuditEvent(auditContext, throttledLocation)
        metricsMonitoringService.sendMonitoringEvents(auditContext, throttledLocation)
        Redirect(throttledLocation.url)
      }
    })
  }

  def sendAuditEvent(auditContext: TAuditContext, throttledLocation: LocationType)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Unit = {
    auditContext.toAuditEvent(throttledLocation).foreach { auditEvent =>
      auditConnector.sendEvent(auditEvent)
      Logger.debug(s"Routing decision summary: ${auditEvent.detail \ "reasons"}")
    }
  }
}

object TarRules extends RuleEngine {

  import Condition._

  private val shouldShowWelcomePage: Condition = LoggedInForTheFirstTime and HasNeverSeenWelcomeBefore

  override val rules: List[Rule] = List(
    when(LoggedInViaVerify) thenGoTo PersonalTaxAccount withName "pta-home-page-for-verify-user",
    when(shouldShowWelcomePage and (LoggedInViaGovernmentGateway and HasAnyBusinessEnrolment)) thenGoTo WelcomeBTA withName "bta-welcome-page-for-user-with-business-enrolments",
    when(LoggedInViaGovernmentGateway and HasAnyBusinessEnrolment) thenGoTo BusinessTaxAccount withName "bta-home-page-for-user-with-business-enrolments",

    when(shouldShowWelcomePage and (LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and not(HasPreviousReturns))) thenGoTo WelcomeBTA withName "bta-welcome-page-for-user-with-no-previous-return",
    when(LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and not(HasPreviousReturns)) thenGoTo BusinessTaxAccount withName "bta-home-page-for-user-with-no-previous-return",

    when(shouldShowWelcomePage and (LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and (IsInAPartnership or IsSelfEmployed))) thenGoTo WelcomeBTA withName "bta-welcome-page-for-user-with-partnership-or-self-employment",
    when(LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and (IsInAPartnership or IsSelfEmployed)) thenGoTo BusinessTaxAccount withName "bta-home-page-for-user-with-partnership-or-self-employment",

    when(shouldShowWelcomePage and (LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and not(IsInAPartnership) and not(IsSelfEmployed))) thenGoTo WelcomePTA withName "pta-welcome-page-for-user-with-no-partnership-and-no-self-employment",
    when(LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and not(IsInAPartnership) and not(IsSelfEmployed)) thenGoTo PersonalTaxAccount withName "pta-home-page-for-user-with-no-partnership-and-no-self-employment",

    when(shouldShowWelcomePage) thenGoTo WelcomeBTA withName "bta-welcome-page-passed-through",
    when(AnyOtherRuleApplied) thenGoTo BusinessTaxAccount withName "bta-home-page-passed-through"
  )
}