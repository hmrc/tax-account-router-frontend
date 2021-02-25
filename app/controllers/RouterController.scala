/*
 * Copyright 2021 HM Revenue & Customs
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

import config.FrontendAppConfig
import engine._
import javax.inject.{Inject, Singleton}
import model._
import play.api.Logger
import play.api.libs.json.{JsNull, JsValue, Json}
import play.api.mvc._
import services._
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolments}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RouterController @Inject()(val authConnector: AuthConnector,
                                  auditConnector: AuditConnector,
                                  ruleEngine: TarRules,
                                  analyticsEventSender: AnalyticsEventSender,
                                  throttlingService: ThrottlingService,
                                  ruleContext: RuleContext,
                                  appConfig: FrontendAppConfig,
                                  val messagesControllerComponents: MessagesControllerComponents,
                                  metricsMonitoringService: MetricsMonitoringService,
                                  externalUrls: ExternalUrls)
                                  (implicit val ec: ExecutionContext)
  extends FrontendController(messagesControllerComponents) with AuthorisedFunctions {

  val account: Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      ruleContext.activeEnrolments.flatMap{ enrolments =>
        implicit val enrolmentsContext: Enrolments = Enrolments(enrolments)
        route
      }
    }.recoverWith {
      case _ =>
        Logger.info(s"unauthorised user - redirecting to login.")
        Future.successful(Redirect(externalUrls.signIn))
    }

  }

  def route(implicit enrolmentsContext: Enrolments, request: Request[AnyContent]): Future[Result] = {
    val destinationAfterRulesApplied = ruleEngine.getLocation(ruleContext)

    val destinationAfterThrottling: Future[(AuditInfo, Location)] = throttlingService.throttle(destinationAfterRulesApplied, ruleContext).run
    val futureAuditInfo = destinationAfterThrottling map { case (auditInfo, _) => auditInfo }
    val futureFinalDestination = destinationAfterThrottling map { case (_, finalDestination) => finalDestination }

    val destination = for {
      finalDestination <- futureFinalDestination
      auditInfo <- futureAuditInfo
    } yield {
      sendAuditEvent(ruleContext, auditInfo, finalDestination)
      metricsMonitoringService.sendMonitoringEvents(auditInfo, finalDestination)
      analyticsEventSender.sendEvents(auditInfo, finalDestination.name)

      Logger.debug(s"Routing to: ${finalDestination.name}")
      finalDestination
    }

    destination.map(location => Redirect(location.url))
  }

  def sendAuditEvent(ruleContext: RuleContext, auditInfo: AuditInfo, throttledLocation: Location)
                    (implicit enrolmentsContext: Enrolments, request: Request[AnyContent]): Unit = {
    val auditEvent: ExtendedDataEvent = auditInfo.toAuditEvent(throttledLocation)
    auditConnector.sendExtendedEvent(auditEvent)
    val reasons: JsValue = (auditEvent.detail \ "reasons").getOrElse(JsNull)

    val extendedLoggingEnabled = appConfig.extendedLoggingEnabled

    if (extendedLoggingEnabled) {
      Logger.warn(s"[AIV-1992] ${auditInfo.ruleApplied.getOrElse("No rule applied")}, " +
        s"Location = ${throttledLocation.name}, " +
        s"Affinity group = ${ruleContext.affinityGroup.value.getOrElse("can not found")}, " +
        s"Enrolments = ${ruleContext.activeEnrolmentKeys.value.getOrElse("can not found")}")
    }
    Logger.debug(s"Routing decision summary: ${Json.stringify(reasons)}")
  }
}
