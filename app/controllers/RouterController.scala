/*
 * Copyright 2017 HM Revenue & Customs
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
import engine.RuleEngine
import model._
import play.api.Logger
import play.api.mvc._
import services._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

object RouterController extends RouterController {
  override protected def authConnector = FrontendAuthConnector

  override val metricsMonitoringService = MetricsMonitoringService

  override val ruleEngine = TarRules

  override val throttlingService = ThrottlingService

  override val auditConnector = FrontendAuditConnector

  override def createAuditContext() = AuditContext()

  override val analyticsEventSender = AnalyticsEventSender
}

trait RouterController extends FrontendController with Actions {

  val metricsMonitoringService: MetricsMonitoringService

  def ruleEngine: RuleEngine

  def throttlingService: ThrottlingService

  def auditConnector: AuditConnector

  def createAuditContext(): TAuditContext

  def analyticsEventSender: AnalyticsEventSender

  val account = AuthenticatedBy(authenticationProvider = RouterAuthenticationProvider, pageVisibility = AllowAll).async { implicit user => request => route(user, request) }

  def route(implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {
    val ruleContext = RuleContext(None)
    val auditContext = createAuditContext()
    getFinalDestination(ruleContext, auditContext).map(location => Redirect(location.fullUrl))
  }

  private def getFinalDestination(ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], authContext: AuthContext) = for {
    destinationAfterRulesApplied <- ruleEngine.matchRulesForLocation(ruleContext, auditContext)
    finalDestination <- throttlingService.throttle(destinationAfterRulesApplied, auditContext, ruleContext)
  } yield {
    sendAuditEvent(auditContext, finalDestination)
    metricsMonitoringService.sendMonitoringEvents(auditContext, finalDestination)
    Logger.debug(s"routing to: ${finalDestination.name}")
    analyticsEventSender.sendEvents(finalDestination.name, auditContext)
    finalDestination
  }

  private def sendAuditEvent(auditContext: TAuditContext, throttledLocation: Location)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier) = {
    auditContext.toAuditEvent(throttledLocation).foreach { auditEvent =>
      auditConnector.sendEvent(auditEvent)
      Logger.debug(s"Routing decision summary: ${auditEvent.detail \ "reasons"}")
    }
  }
}
