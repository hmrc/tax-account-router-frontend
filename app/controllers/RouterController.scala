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
import org.joda.time.{DateTime, Seconds}
import play.api.Play.current
import play.api.mvc._
import play.api.{Logger, Play}
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

  val cookieMaxAge = Map(
    (PersonalTaxAccount, PersonalTaxAccount) -> Instant(DateTime.parse("2020-01-01T00:00")),
    (BusinessTaxAccount, BusinessTaxAccount) -> Duration(14400),
    (PersonalTaxAccount, BusinessTaxAccount) -> Duration(14400)
  )

  val cookieValues = Map(
    PersonalTaxAccount -> PersonalTaxAccount,
    BusinessTaxAccount -> BusinessTaxAccount,
    WelcomeBTA -> BusinessTaxAccount,
    WelcomePTA -> PersonalTaxAccount
  )

  val throttlingEnabled = Play.configuration.getBoolean("throttling.enabled").getOrElse(false)

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

    val nextLocation = ruleEngine.getLocation(authContext, ruleContext, auditContext)

    nextLocation.map(locationCandidate => {
      val location = locationCandidate.getOrElse(defaultLocation)

      // TODO: read cookies (if throttling enabled?)
      val routedDestinationCookie: Option[Cookie] = request.cookies.get("routed_destination")
      val finalDestination = routedDestinationCookie match {
        case Some(cookie) if Location.locations.get(cookie.value).contains(location) && throttlingEnabled =>
          request.cookies.get("throttled_destination").flatMap(cookie => Location.locations.get(cookie.value)).get
        case _ =>
          throttlingService.throttle(location, auditContext)
      }

      Logger.debug(s"routing to: ${finalDestination.name}")
      sendAuditEvent(auditContext, finalDestination)
      metricsMonitoringService.sendMonitoringEvents(auditContext, finalDestination)

      if (throttlingEnabled) {
        // TODO: should the cookies max-age be refreshed?
        val maxAge = for {
          routedDestination <- cookieValues.get(location)
          throttledDestination <- cookieValues.get(finalDestination)
          maxAge <- cookieMaxAge.get((routedDestination, throttledDestination))
        } yield maxAge.getMaxAge

        Redirect(finalDestination.url).withCookies(
          Cookie("routed_destination", location.name, maxAge = maxAge.fold(Some(0))(v => Some(v))),
          Cookie("throttled_destination", finalDestination.name, maxAge = maxAge.fold(Some(0))(v => Some(v)))
        )
      } else {
        // TODO: create the cookie (if throttling enabled?)
        // TODO: delete cookies in this case?
        Redirect(finalDestination.url).discardingCookies(DiscardingCookie("routed_destination"), DiscardingCookie("throttled_destination"))
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

trait CookieMaxAge {
  def getMaxAge: Int
}

case class Duration(seconds: Int) extends CookieMaxAge {
  override def getMaxAge: Int = seconds
}

case class Instant(expirationTime: DateTime) extends CookieMaxAge {
  override def getMaxAge: Int = Seconds.secondsBetween(DateTime.now(), expirationTime).getSeconds
}