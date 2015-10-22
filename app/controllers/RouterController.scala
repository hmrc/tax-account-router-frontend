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

import scala.concurrent.Future

object RouterController extends RouterController {
  override protected def authConnector: AuthConnector = FrontendAuthConnector

  override val defaultLocation: LocationType = BusinessTaxAccount

  override val controllerMetrics: ControllerMetrics = ControllerMetrics

  override val ruleEngine: RuleEngine = TarRules

  override def throttlingService: ThrottlingService = ThrottlingService

  override def auditConnector: AuditConnector = FrontendAuditConnector

  override def createAuditContext(): TAuditContext = AuditContext()
}

trait RouterController extends FrontendController with Actions {

  val controllerMetrics: ControllerMetrics

  def defaultLocation: LocationType

  def ruleEngine: RuleEngine

  def throttlingService: ThrottlingService

  def auditConnector: AuditConnector

  def createAuditContext(): TAuditContext

  val account = AuthenticatedBy(RouterAuthenticationProvider).async { implicit user => request => route(user, request) }

  def route(implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {

    val ruleContext = RuleContext(authContext)

    val auditContext: TAuditContext = createAuditContext()

    val nextLocation: Future[Option[LocationType]] = ruleEngine.getLocation(authContext, ruleContext, auditContext)

    nextLocation.map(locationCandidate => {
      val location: LocationType = locationCandidate.getOrElse(defaultLocation)
      controllerMetrics.registerRedirectFor(location.name)
      val throttledLocation: LocationType = throttlingService.throttle(location, auditContext)
      auditContext.toAuditEvent(throttledLocation.url).foreach { auditEvent =>
        Logger.debug(s"Routing decision summary: ${auditEvent.detail}")
        auditConnector.sendEvent(auditEvent)
      }
      Redirect(throttledLocation.url)
    })
  }
}

object TarRules extends RuleEngine {
  import Condition._

  override val rules: List[Rule] = List(
    when(LoggedInForTheFirstTime and not(HasSeenWelcomeBefore)) thenGoTo Welcome,
    when(LoggedInViaVerify) thenGoTo PersonalTaxAccount,
    when(LoggedInViaGovernmentGateway and HasAnyBusinessEnrolment) thenGoTo BusinessTaxAccount,
    when(LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and not(HasPreviousReturns)) thenGoTo BusinessTaxAccount,
    when(LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and (IsInAPartnership or IsSelfEmployed)) thenGoTo BusinessTaxAccount,
    when(LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and not(IsInAPartnership) and not(IsSelfEmployed)) thenGoTo PersonalTaxAccount,
    when(AllOtherRulesFailed) thenGoTo BusinessTaxAccount
  )
}