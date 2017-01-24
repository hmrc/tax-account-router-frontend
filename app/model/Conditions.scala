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

package model

import connector.AffinityGroupValue
import engine.Condition
import model.RoutingReason._
import play.api.Play
import play.api.Play.current
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

object GGEnrolmentsAvailable extends Condition {
  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.enrolments.map(_ => true).recover { case _ => false }

  override val auditType = Some(GG_ENROLMENTS_AVAILABLE)
}

object AffinityGroupAvailable extends Condition {
  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.affinityGroup.map(_ => true).recover { case _ => false }

  override val auditType = Some(AFFINITY_GROUP_AVAILABLE)
}

trait HasAnyBusinessEnrolment extends Condition {
  def businessEnrolments: Set[String]

  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.activeEnrolmentKeys.map(_.intersect(businessEnrolments).nonEmpty)

  override val auditType = Some(HAS_BUSINESS_ENROLMENTS)
}

object HasAnyBusinessEnrolment extends HasAnyBusinessEnrolment {
  override lazy val businessEnrolments = Play.configuration.getString("business-enrolments").getOrElse("").split(",").map(_.trim).toSet[String]
}

object SAReturnAvailable extends Condition {
  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.lastSaReturn.map(_ => true).recover { case _ => false }

  override val auditType = Some(SA_RETURN_AVAILABLE)
}

trait HasSaEnrolments extends Condition {
  def saEnrolments: Set[String]

  override val auditType = Some(HAS_SA_ENROLMENTS)

  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.activeEnrolmentKeys.map(_.intersect(saEnrolments).nonEmpty)
}

object HasSaEnrolments extends HasSaEnrolments {
  override lazy val saEnrolments = Play.configuration.getString("self-assessment-enrolments").getOrElse("").split(",").map(_.trim).toSet[String]
}

object HasPreviousReturns extends Condition {

  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.lastSaReturn.map(_.previousReturns)

  override val auditType = Some(HAS_PREVIOUS_RETURNS)
}

object IsInAPartnership extends Condition {
  override val auditType = Some(IS_IN_A_PARTNERSHIP)

  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.lastSaReturn.map(_.partnership)
}

object IsSelfEmployed extends Condition {
  override val auditType = Some(IS_SELF_EMPLOYED)

  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.lastSaReturn.map(_.selfEmployment)
}

object LoggedInViaVerify extends Condition {
  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    Future.successful(!request.session.data.contains("token") &&  ruleContext.credId.isEmpty)

  override val auditType = Some(IS_A_VERIFY_USER)
}

object IsAGovernmentGatewayUser extends Condition {
  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    Future.successful(request.session.data.contains("token") || ruleContext.credId.isDefined)

  override val auditType = Some(IS_A_GOVERNMENT_GATEWAY_USER)
}

object HasNino extends Condition {
  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.authority.map(_.nino.isDefined)

  override val auditType = Some(HAS_NINO)
}

object AnyOtherRuleApplied extends Condition {
  override val auditType = None

  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) = Future.successful(true)
}

object HasIndividualAffinityGroup extends Condition {

  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.affinityGroup.map(_ == AffinityGroupValue.INDIVIDUAL)

  override val auditType = Some(HAS_INDIVIDUAL_AFFINITY_GROUP)
}

object HasAnyInactiveEnrolment extends Condition {

  override def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) =
    ruleContext.notActivatedEnrolmentKeys.map(_.nonEmpty)

  override val auditType = Some(HAS_ANY_INACTIVE_ENROLMENT)
}
