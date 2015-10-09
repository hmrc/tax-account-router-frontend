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
import model._
import play.api.mvc._
import model.Location._
import services.{RuleService, WelcomePageService}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

object RouterController extends RouterController {
  override protected def authConnector: AuthConnector = FrontendAuthConnector

  override val welcomePageService: WelcomePageService = WelcomePageService

  override val defaultLocation: LocationType = BTA

  override val controllerMetrics: ControllerMetrics = ControllerMetrics

  override val rules: List[Rule] = List(WelcomePageRule, VerifyRule, GovernmentGatewayRule)

  override def ruleService: RuleService = RuleService

  override def auditConnector: AuditConnector = FrontendAuditConnector

  override def createAuditContext(): TAuditContext = AuditContext()
}

trait RouterController extends FrontendController with Actions {

  val controllerMetrics: ControllerMetrics

  val welcomePageService: WelcomePageService

  def defaultLocation: LocationType

  def rules: List[Rule]

  def ruleService: RuleService

  def auditConnector: AuditConnector

  def createAuditContext(): TAuditContext

  val account = AuthenticatedBy(RouterAuthenticationProvider).async { implicit user => request => route(user, request) }

  def route(implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {

    val ruleContext = RuleContext(request.session.data.getOrElse("name", ""))

    val auditContext: TAuditContext = createAuditContext()

    val nextLocation: Future[Option[LocationType]] = ruleService.fireRules(rules, authContext, ruleContext, auditContext)

    nextLocation.map(locationCandidate => {
      val location: LocationType = locationCandidate.getOrElse(defaultLocation)
      controllerMetrics.registerRedirectFor(location.name)
      auditConnector.sendEvent(auditContext.toAuditEvent(location.url))
      Redirect(location.url)
    })
  }
}