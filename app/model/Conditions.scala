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
import engine._
import engine.RoutingReason._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

object Conditions {
  object functions {
    type ConditionF = (RuleContext) => Future[Boolean]
    val ggEnrolmentsAvailableF: ConditionF = rc =>
      rc.enrolments.map(_ => true).recover { case _ => false }

    val affinityGroupAvailableF: ConditionF = rc =>
      rc.affinityGroup.map(_ => true).recover { case _ => false }

    val loggedInViaVerifyF: ConditionF = rc =>
      Future.successful(!rc.request_.session.data.contains("token") && rc.credId.isEmpty)

    val hasAnyBusinessEnrolmentF: ConditionF = rc =>
      rc.activeEnrolmentKeys.map(_.intersect(rc.businessEnrolments).nonEmpty)

    val SAReturnAvailableF: ConditionF = rc =>
      rc.lastSaReturn.map(_ => true).recover { case _ => false }

    val hasSaEnrolmentsF: ConditionF = rc =>
      rc.activeEnrolmentKeys.map(_.intersect(rc.saEnrolments).nonEmpty)

    val hasPreviousReturnsF: ConditionF = rc =>
      rc.lastSaReturn.map(_.previousReturns)

    val isInAPartnershipF: ConditionF = rc =>
      rc.lastSaReturn.map(_.partnership)

    val isSelfEmployedF: ConditionF = rc =>
      rc.lastSaReturn.map(_.selfEmployment)

    val isAGovernmentGatewayUserF: ConditionF = rc =>
      Future.successful(rc.request_.session.data.contains("token") || rc.credId.isDefined)

    val hasNinoF: ConditionF = rc =>
      rc.authority.map(_.nino.isDefined)

    val hasIndividualAffinityGroupF: ConditionF = rc =>
    rc.affinityGroup.map(_ == AffinityGroupValue.INDIVIDUAL)

    val hasAnyInactiveEnrolmentF: ConditionF = rc =>
      rc.notActivatedEnrolmentKeys.map(_.nonEmpty)

    val anyOtherRuleAppliedF: ConditionF = _ =>
      Future.successful(true)
  }

  import functions._

  val ggEnrolmentsAvailable: Condition[RuleContext] = Pure(ggEnrolmentsAvailableF, GG_ENROLMENTS_AVAILABLE)
  val affinityGroupAvailable = Pure(affinityGroupAvailableF, AFFINITY_GROUP_AVAILABLE)
  val loggedInViaVerify = Pure(loggedInViaVerifyF, IS_A_VERIFY_USER)
  val hasAnyBusinessEnrolment = Pure(hasAnyBusinessEnrolmentF, HAS_BUSINESS_ENROLMENTS)
  val saReturnAvailable = Pure(SAReturnAvailableF, SA_RETURN_AVAILABLE)
  val hasSaEnrolments = Pure(hasSaEnrolmentsF, HAS_SA_ENROLMENTS)
  val hasPreviousReturns = Pure(hasPreviousReturnsF, HAS_PREVIOUS_RETURNS)
  val isInAPartnership = Pure(isInAPartnershipF, IS_IN_A_PARTNERSHIP)
  val isSelfEmployed = Pure(isSelfEmployedF, IS_SELF_EMPLOYED)
  val isAGovernmentGatewayUser = Pure(isAGovernmentGatewayUserF, IS_A_GOVERNMENT_GATEWAY_USER)
  val hasNino = Pure(hasNinoF, HAS_NINO)
  val hasIndividualAffinityGroup = Pure(hasIndividualAffinityGroupF, HAS_INDIVIDUAL_AFFINITY_GROUP)
  val hasAnyInactiveEnrolment = Pure(hasAnyInactiveEnrolmentF, HAS_ANY_INACTIVE_ENROLMENT)
}