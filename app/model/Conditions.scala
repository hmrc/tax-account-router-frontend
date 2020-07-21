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

package model

import config.FrontendAppConfig
import engine.RoutingReason._
import engine._
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Conditions @Inject()(appConfig: FrontendAppConfig)(implicit val ec: ExecutionContext){
  object predicates {

    type ConditionPredicate = (RuleContext) => Future[Boolean]

    val ggEnrolmentsAvailableF: ConditionPredicate = rc =>
      rc.enrolments.map(_ => true).recover { case _ => false }

    val affinityGroupAvailableF: ConditionPredicate = rc =>
      rc.affinityGroup.map(_ => true).recover { case _ => false }

    val loggedInViaVerifyF: ConditionPredicate = rc =>
      for {
        isVerifyUser <- rc.isVerifyUser
      } yield {
        !rc.request_.session.data.contains("token") || isVerifyUser
      }

    val hasAnyBusinessEnrolmentF: ConditionPredicate = rc =>
      rc.activeEnrolmentKeys.map(_.intersect(appConfig.businessEnrolments).nonEmpty)

    val saReturnAvailableF: ConditionPredicate = rc =>
      rc.lastSaReturn.map(_ => true).recover { case _ => false }

    val hasSaEnrolmentsF: ConditionPredicate = rc =>
      rc.activeEnrolmentKeys.map(_.intersect(appConfig.saEnrolments).nonEmpty)

    val hasPreviousReturnsF: ConditionPredicate = rc =>
      rc.lastSaReturn.map(_.previousReturns)

    val isInAPartnershipF: ConditionPredicate = rc =>
      rc.lastSaReturn.map(_.partnership)

    val isSelfEmployedF: ConditionPredicate = rc =>
      rc.lastSaReturn.map(_.selfEmployment)

    val isAGovernmentGatewayUserF: ConditionPredicate = rc =>
      for {
        isGovernmentGatewayUser <- rc.isGovernmentGatewayUser
      } yield {
        rc.request_.session.data.contains("token") || isGovernmentGatewayUser
      }

    val hasNinoF: ConditionPredicate = rc => rc.hasNino

    val hasIndividualAffinityGroupF: ConditionPredicate = rc =>
    rc.affinityGroup.map(_ == AffinityGroupValue.INDIVIDUAL)

    val hasAnyInactiveEnrolmentF: ConditionPredicate = rc =>
      rc.notActivatedEnrolmentKeys.map(_.nonEmpty)
  }

  import predicates._

  val ggEnrolmentsAvailable: Pure[RuleContext] = Pure(ggEnrolmentsAvailableF, GG_ENROLMENTS_AVAILABLE)
  val affinityGroupAvailable: Pure[RuleContext] = Pure(affinityGroupAvailableF, AFFINITY_GROUP_AVAILABLE)
  val loggedInViaVerify: Pure[RuleContext] = Pure(loggedInViaVerifyF, IS_A_VERIFY_USER)
  val hasAnyBusinessEnrolment: Pure[RuleContext] = Pure(hasAnyBusinessEnrolmentF, HAS_BUSINESS_ENROLMENTS)
  val saReturnAvailable: Pure[RuleContext] = Pure(saReturnAvailableF, SA_RETURN_AVAILABLE)
  val hasSaEnrolments: Pure[RuleContext] = Pure(hasSaEnrolmentsF, HAS_SA_ENROLMENTS)
  val hasPreviousReturns: Pure[RuleContext] = Pure(hasPreviousReturnsF, HAS_PREVIOUS_RETURNS)
  val isInAPartnership: Pure[RuleContext] = Pure(isInAPartnershipF, IS_IN_A_PARTNERSHIP)
  val isSelfEmployed: Pure[RuleContext] = Pure(isSelfEmployedF, IS_SELF_EMPLOYED)
  val isAGovernmentGatewayUser: Pure[RuleContext] = Pure(isAGovernmentGatewayUserF, IS_A_GOVERNMENT_GATEWAY_USER)
  val hasNino: Pure[RuleContext] = Pure(hasNinoF, HAS_NINO)
  val hasIndividualAffinityGroup: Pure[RuleContext] = Pure(hasIndividualAffinityGroupF, HAS_INDIVIDUAL_AFFINITY_GROUP)
  val hasAnyInactiveEnrolment: Pure[RuleContext] = Pure(hasAnyInactiveEnrolmentF, HAS_ANY_INACTIVE_ENROLMENT)

}

