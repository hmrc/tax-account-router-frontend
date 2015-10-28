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
import com.codahale.metrics.MetricRegistry
import config.FrontendAuditConnector
import connector.FrontendAuthConnector
import engine.{Condition, Rule, RuleEngine}
import model.Location._
import model._
import play.api.Logger
import play.api.mvc._
import services._
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

object RouterController extends RouterController {
  override protected def authConnector: AuthConnector = FrontendAuthConnector

  override val defaultLocation: LocationType = BusinessTaxAccount

  override val monitoringMetrics: MonitoringMetrics = MonitoringMetrics

  override val ruleEngine: RuleEngine = TarRules

  override def throttlingService: ThrottlingService = ThrottlingService

  override def auditConnector: AuditConnector = FrontendAuditConnector

  override def createAuditContext(): TAuditContext = AuditContext()
}

trait RouterController extends FrontendController with Actions {

  val monitoringMetrics: MonitoringMetrics

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
      val throttledLocation: LocationType = throttlingService.throttle(location, auditContext)
      sendAuditEvent(auditContext, throttledLocation)
      sendMonitoringEvents(auditContext, throttledLocation)
      Redirect(throttledLocation.url)
    })
  }

  def sendMonitoringEvents(auditContext: TAuditContext, throttledLocation: LocationType)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Unit = {
    val registry: MetricRegistry = monitoringMetrics.registry
    registry.meter("routed-users").mark()
    registry.meter(s"routed-to-${throttledLocation.name}").mark()

    val trueConditions = auditContext.getReasons.filter { case (k, v) => v == "true" }.keys
    trueConditions.foreach(monitoringMetrics.registry.meter(_).mark())

    val falseConditions = auditContext.getReasons.filter { case (k, v) => v == "false" }.keys
    falseConditions.foreach(key => monitoringMetrics.registry.meter(s"not-$key").mark())

    val destinationUrlBeforeThrottling = auditContext.getThrottlingDetails.get("destination-url-before-throttling")
    val destinationUrlAfterThrottling = throttledLocation.url
    if (destinationUrlBeforeThrottling.isDefined && destinationUrlBeforeThrottling.get != destinationUrlAfterThrottling) {

      val destinationNameBeforeThrottling = auditContext.getThrottlingDetails.getOrElse("destination-name-before-throttling", "")
      val destinationNameAfterThrottling = throttledLocation.name
      registry.meter(s"$destinationNameBeforeThrottling-throttled-to-$destinationNameAfterThrottling").mark()
    }
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

  override val rules: List[Rule] = List(
    when(LoggedInForTheFirstTime and HasNeverSeenWelcomeBefore) thenGoTo Welcome,
    when(LoggedInViaVerify) thenGoTo PersonalTaxAccount,
    when(LoggedInViaGovernmentGateway and HasAnyBusinessEnrolment) thenGoTo BusinessTaxAccount,
    when(LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and not(HasPreviousReturns)) thenGoTo BusinessTaxAccount,
    when(LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and (IsInAPartnership or IsSelfEmployed)) thenGoTo BusinessTaxAccount,
    when(LoggedInViaGovernmentGateway and HasSelfAssessmentEnrolments and not(IsInAPartnership) and not(IsSelfEmployed)) thenGoTo PersonalTaxAccount,
    when(AllOtherRulesFailed) thenGoTo BusinessTaxAccount
  )
}