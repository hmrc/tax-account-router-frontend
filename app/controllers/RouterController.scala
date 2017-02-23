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
import engine._

import scala.concurrent.Future

object RouterController extends RouterController {
  override protected def authConnector = FrontendAuthConnector

  override val metricsMonitoringService = MetricsMonitoringService

  override val ruleEngine = TarRules

  override val throttlingService = Throttling

  override val auditConnector = FrontendAuditConnector

  override val analyticsEventSender = AnalyticsEventSender
}

trait RouterController extends FrontendController with Actions {

  val metricsMonitoringService: MetricsMonitoringService

  def ruleEngine: RuleEngine

  def throttlingService: Throttling

  def auditConnector: AuditConnector

  def analyticsEventSender: AnalyticsEventSender

  val account = AuthenticatedBy(authenticationProvider = RouterAuthenticationProvider, pageVisibility = AllowAll).async { implicit user => request => route(user, request) }

  def route(implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {
    val ruleContext = RuleContext(None)

    def sendAuditEvent(auditInfo: AuditInfo, throttledLocation: Location)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier) = {
      val auditEvent = auditInfo.toAuditEvent(throttledLocation)
      auditConnector.sendEvent(auditEvent)
      Logger.debug(s"Routing decision summary: ${auditEvent.detail \ "reasons"}")
    }

    val destinationAfterRulesApplied = ruleEngine.getLocation(ruleContext)
    val finalResult = throttlingService.doThrottle(destinationAfterRulesApplied, ruleContext).run
    val futureAuditInfo = finalResult map { case (auditInfo, _) => auditInfo }
    val futureFinalDestination = finalResult map { case (_, finalDestination) => finalDestination }

    val destination = for {
      finalDestination <- futureFinalDestination
      auditInfo <- futureAuditInfo
    } yield {
      sendAuditEvent(auditInfo, finalDestination)
      metricsMonitoringService.sendMonitoringEvents(auditInfo, finalDestination)
      Logger.debug(s"routing to: ${finalDestination.name}")
      analyticsEventSender.sendEvents(finalDestination.name, auditInfo)
      finalDestination
    }

    destination.map(location => Redirect(location.url))
  }
}
