/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.{Play, Logger}
import play.api.mvc._
import services._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.controller.FrontendController
import engine._
import play.api.libs.json.{JsNull, JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import play.api.Play.current

import scala.concurrent.Future

object RouterController extends RouterController {
  override protected def authConnector = FrontendAuthConnector

  override val metricsMonitoringService = MetricsMonitoringService

  override val ruleEngine = TarRules

  override val throttlingService = ThrottlingService

  override val auditConnector = FrontendAuditConnector

  override val analyticsEventSender = AnalyticsEventSender
}

trait RouterController extends FrontendController with Actions {

  val metricsMonitoringService: MetricsMonitoringService

  def ruleEngine: RuleEngine

  def throttlingService: ThrottlingService

  def auditConnector: AuditConnector

  def analyticsEventSender: AnalyticsEventSender

  val account = AuthenticatedBy(authenticationProvider = RouterAuthenticationProvider, pageVisibility = AllowAll).async {
    implicit authContext => request =>
      route(authContext, request)
  }

  def route(implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {
    val ruleContext = RuleContext(None)

    def sendAuditEvent(auditInfo: AuditInfo, throttledLocation: Location)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier) = {
      val auditEvent = auditInfo.toAuditEvent(throttledLocation)
      auditConnector.sendExtendedEvent(auditEvent)
      val reasons: JsValue = (auditEvent.detail \ "reasons").getOrElse(JsNull)

      val extendedLoggingEnabled = Play.configuration.getBoolean("extended-logging-enabled").getOrElse(false)

      if (extendedLoggingEnabled) {
        Logger.warn(s"[AIV-1264] ${auditInfo.ruleApplied.getOrElse("No rule applied.")} , [AIV-1992] Location = ${throttledLocation}" +
          s" affinity group = ${ruleContext.affinityGroup} , Enrolments = ${ruleContext.activeEnrolmentKeys}")
      }
      Logger.debug(s"Routing decision summary: ${Json.stringify(reasons)}")
    }

    val destinationAfterRulesApplied = ruleEngine.getLocation(ruleContext)
    val destinationAfterThrottling: Future[(AuditInfo, Location)] = throttlingService.throttle(destinationAfterRulesApplied, ruleContext).run
    val futureAuditInfo = destinationAfterThrottling map { case (auditInfo, _) => auditInfo }
    val futureFinalDestination = destinationAfterThrottling map { case (_, finalDestination) => finalDestination }

    val destination = for {
      finalDestination <- futureFinalDestination
      auditInfo <- futureAuditInfo
    } yield {
      sendAuditEvent(auditInfo, finalDestination)
      metricsMonitoringService.sendMonitoringEvents(auditInfo, finalDestination)
      analyticsEventSender.sendEvents(auditInfo, finalDestination.name)

      Logger.debug(s"Routing to: ${finalDestination.name}")
      finalDestination
    }

    destination.map(location => Redirect(location.url))
  }
}
