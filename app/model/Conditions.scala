/*
 * Copyright 2016 HM Revenue & Customs
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

import connector.AffinityGroupValue
import engine.Condition
import model.RoutingReason._
import play.api.Play
import play.api.Play.current
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain.CredentialStrength
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

object GGEnrolmentsAvailable extends Condition {
  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.activeEnrolments.map(_ => true).recover { case _ => false}

  override val auditType = Some(GG_ENROLMENTS_AVAILABLE)
}

trait HasAnyBusinessEnrolment extends Condition {
  def businessEnrolments: Set[String]

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.activeEnrolments.map(_.intersect(businessEnrolments).nonEmpty)

  override val auditType = Some(HAS_BUSINESS_ENROLMENTS)
}

object HasAnyBusinessEnrolment extends HasAnyBusinessEnrolment {
  override lazy val businessEnrolments = Play.configuration.getString("business-enrolments").getOrElse("").split(",").map(_.trim).toSet[String]
}

object SAReturnAvailable extends Condition {
  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.lastSaReturn.map(_ => true).recover { case _ => false}

  override val auditType = Some(SA_RETURN_AVAILABLE)
}

trait HasSelfAssessmentEnrolments extends Condition {
  def selfAssessmentEnrolments: Set[String]

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.activeEnrolments.map(_.intersect(selfAssessmentEnrolments).nonEmpty)

  override val auditType = Some(HAS_SA_ENROLMENTS)
}

object HasSelfAssessmentEnrolments extends HasSelfAssessmentEnrolments {
  override lazy val selfAssessmentEnrolments = Play.configuration.getString("self-assessment-enrolments").getOrElse("").split(",").map(_.trim).toSet[String]
}

object HasPreviousReturns extends Condition {

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.lastSaReturn.map(_.previousReturns)

  override val auditType = Some(HAS_PREVIOUS_RETURNS)
}

object IsInAPartnership extends Condition {
  override val auditType = Some(IS_IN_A_PARTNERSHIP)

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.lastSaReturn.map(_.partnership)
}

object IsSelfEmployed extends Condition {
  override val auditType = Some(IS_SELF_EMPLOYED)

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.lastSaReturn.map(_.selfEmployment)
}

object LoggedInViaVerify extends Condition {
  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    Future.successful(!request.session.data.contains("token"))

  override val auditType = Some(IS_A_VERIFY_USER)
}

object LoggedInViaGovernmentGateway extends Condition {
  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    Future.successful(request.session.data.contains("token"))

  override val auditType = Some(IS_A_GOVERNMENT_GATEWAY_USER)
}

object HasNino extends Condition {
  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    Future.successful(authContext.principal.accounts.paye.isDefined)

  override val auditType = Some(HAS_NINO)
}

object AnyOtherRuleApplied extends Condition {
  override val auditType = None

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) = Future.successful(true)
}

object HasSaUtr extends Condition {
  override val auditType = Some(HAS_SA_UTR)

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    Future.successful(authContext.principal.accounts.sa.isDefined)
}

object HasRegisteredFor2SV extends Condition {
  override val auditType = Some(HAS_REGISTERED_FOR_2SV)

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.currentCoAFEAuthority.map(_.twoFactorAuthOtpId.isDefined)
}

object HasStrongCredentials extends Condition {
  override val auditType = Some(HAS_STRONG_CREDENTIALS)

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    Future.successful(authContext.user.credentialStrength == CredentialStrength.Strong)
}

object HasOnlyOneEnrolment extends Condition {

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.activeEnrolments.map(_.size == 1)

  override val auditType = Some(HAS_ONLY_ONE_ENROLMENT)
}

object HasIndividualAffinityGroup extends Condition {

  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.affinityGroup.map(_.equalsIgnoreCase(AffinityGroupValue.INDIVIDUAL))

  override val auditType = Some(HAS_INDIVIDUAL_AFFINITY_GROUP)
}
