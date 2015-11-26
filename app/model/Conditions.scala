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

package model

import engine.Condition
import model.RoutingReason._
import play.api.Play
import play.api.Play.current
import play.api.mvc.{AnyContent, Request}
import services.WelcomePageService
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

object HasAnyBusinessEnrolment extends Condition {
  lazy val businessEnrolments: Set[String] = Play.configuration.getStringSeq("business-enrolments").getOrElse(Seq()).toSet[String]

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    ruleContext.activeEnrolments.map(_.intersect(businessEnrolments).nonEmpty)

  override val auditType: Option[RoutingReason] = Some(HAS_BUSINESS_ENROLMENTS)
}

object HasSelfAssessmentEnrolments extends Condition {
  lazy val selfAssessmentEnrolments: Set[String] = Play.configuration.getStringSeq("self-assessment-enrolments").getOrElse(Seq()).toSet[String]

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    ruleContext.activeEnrolments.map(_.intersect(selfAssessmentEnrolments).nonEmpty)

  override val auditType: Option[RoutingReason] = Some(HAS_SA_ENROLMENTS)
}

object HasPreviousReturns extends Condition {

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    ruleContext.lastSaReturn.map(_.previousReturns)

  override val auditType: Option[RoutingReason] = Some(HAS_PREVIOUS_RETURNS)
}

object IsInAPartnership extends Condition {
  override val auditType: Option[RoutingReason] = Some(IS_IN_A_PARTNERSHIP)

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    ruleContext.lastSaReturn.map(_.partnership)
}

object IsSelfEmployed extends Condition {
  override val auditType: Option[RoutingReason] = Some(IS_SELF_EMPLOYED)

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    ruleContext.lastSaReturn.map(_.selfEmployment)
}

trait HasNeverSeenWelcomeBefore extends Condition {
  val welcomePageService: WelcomePageService

  override val auditType: Option[RoutingReason] = Some(HAS_NEVER_SEEN_WELCOME_PAGE_BEFORE)

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    welcomePageService.hasNeverSeenWelcomePageBefore(authContext)
}

object HasNeverSeenWelcomeBefore extends HasNeverSeenWelcomeBefore {
  override val welcomePageService: WelcomePageService = WelcomePageService
}

object LoggedInForTheFirstTime extends Condition {
  override val auditType: Option[RoutingReason] = Some(LOGGED_IN_FOR_THE_FIRST_TIME)

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    Future(authContext.user.previouslyLoggedInAt.isEmpty)
}

object LoggedInViaVerify extends Condition {
  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    Future(!request.session.data.contains("token"))

  override val auditType: Option[RoutingReason] = Some(IS_A_VERIFY_USER)
}

object LoggedInViaGovernmentGateway extends Condition {
  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    Future(request.session.data.contains("token"))

  override val auditType: Option[RoutingReason] = Some(IS_A_GOVERNMENT_GATEWAY_USER)
}

object AnyOtherRuleApplied extends Condition {
  override val auditType: Option[RoutingReason] = None

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(true)
}