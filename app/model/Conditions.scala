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
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Conditions @Inject()(frontendAppConfig: FrontendAppConfig)(implicit val ec: ExecutionContext){
  object predicates {

    type ConditionPredicate = RuleContext => Future[Boolean]

    def ggEnrolmentsAvailableF(implicit hc: HeaderCarrier): ConditionPredicate = rc =>
      rc.enrolments.map( _ => true).recover { case _ => false }

    def affinityGroupAvailableF(implicit hc: HeaderCarrier): ConditionPredicate = rc =>
      rc.affinityGroup.map( _ => true).recover { case _ => false }

    def loggedInViaVerifyF(implicit request: Request[AnyContent], hc: HeaderCarrier): ConditionPredicate = rc => {
      for {
        isVerifyUser <- rc.isVerifyUser
      } yield isVerifyUser
    }

    def hasAnyBusinessEnrolmentF(implicit hc: HeaderCarrier): ConditionPredicate = rc => {
      rc.activeEnrolmentKeys.map(_.intersect(frontendAppConfig.businessEnrolments).nonEmpty)
    }

    def saReturnAvailableF(implicit hc: HeaderCarrier): ConditionPredicate = rc =>
      rc.lastSaReturn.map(_ => true).recover { case _ => false }

    def hasSaEnrolmentsF(implicit hc: HeaderCarrier): ConditionPredicate = rc =>
      rc.activeEnrolmentKeys.map(_.intersect(frontendAppConfig.saEnrolments).nonEmpty)

    def hasPreviousReturnsF(implicit hc: HeaderCarrier): ConditionPredicate = rc =>
      rc.lastSaReturn.map(_.previousReturns)

    def isInAPartnershipF(implicit hc: HeaderCarrier): ConditionPredicate = rc =>
      rc.lastSaReturn.map(_.partnership)

    def isSelfEmployedF(implicit hc: HeaderCarrier): ConditionPredicate = rc =>
      rc.lastSaReturn.map(_.selfEmployment)

    def isAGovernmentGatewayUserF(implicit request: Request[AnyContent], hc: HeaderCarrier): ConditionPredicate = rc =>
      for {
        isGovernmentGatewayUser <- rc.isGovernmentGatewayUser
      } yield {
        request.session.data.contains("token") || isGovernmentGatewayUser
      }

    def hasNinoF(implicit hc: HeaderCarrier): ConditionPredicate = rc => rc.hasNino

    def hasIndividualAffinityGroupF(implicit hc: HeaderCarrier): ConditionPredicate = rc =>
    rc.affinityGroup.map(_ == AffinityGroupValue.INDIVIDUAL)

    def hasAnyInactiveEnrolmentF(implicit hc: HeaderCarrier): ConditionPredicate = rc =>
      rc.notActivatedEnrolmentKeys.map(_.nonEmpty)
  }

  import predicates._

  def ggEnrolmentsAvailable(implicit hc: HeaderCarrier): Pure[RuleContext] = Pure(ggEnrolmentsAvailableF, GG_ENROLMENTS_AVAILABLE)
  def affinityGroupAvailable(implicit hc: HeaderCarrier): Pure[RuleContext] = Pure(affinityGroupAvailableF, AFFINITY_GROUP_AVAILABLE)
  def loggedInViaVerify(implicit request: Request[AnyContent], hc: HeaderCarrier): Pure[RuleContext] = Pure(loggedInViaVerifyF, IS_A_VERIFY_USER)
  def hasAnyBusinessEnrolment(implicit hc: HeaderCarrier): Pure[RuleContext] = Pure(hasAnyBusinessEnrolmentF, HAS_BUSINESS_ENROLMENTS)
  def saReturnAvailable(implicit hc: HeaderCarrier): Pure[RuleContext] = Pure(saReturnAvailableF, SA_RETURN_AVAILABLE)
  def hasSaEnrolments(implicit hc: HeaderCarrier): Pure[RuleContext] = Pure(hasSaEnrolmentsF, HAS_SA_ENROLMENTS)
  def hasPreviousReturns(implicit hc: HeaderCarrier): Pure[RuleContext] = Pure(hasPreviousReturnsF, HAS_PREVIOUS_RETURNS)
  def isInAPartnership(implicit hc: HeaderCarrier): Pure[RuleContext] = Pure(isInAPartnershipF, IS_IN_A_PARTNERSHIP)
  def isSelfEmployed(implicit hc: HeaderCarrier): Pure[RuleContext] = Pure(isSelfEmployedF, IS_SELF_EMPLOYED)
  def isAGovernmentGatewayUser(implicit request: Request[AnyContent], hc: HeaderCarrier): Pure[RuleContext] = Pure(isAGovernmentGatewayUserF, IS_A_GOVERNMENT_GATEWAY_USER)
  def hasNino(implicit hc: HeaderCarrier): Pure[RuleContext] = Pure(hasNinoF, HAS_NINO)
  def hasIndividualAffinityGroup(implicit hc: HeaderCarrier): Pure[RuleContext] = Pure(hasIndividualAffinityGroupF, HAS_INDIVIDUAL_AFFINITY_GROUP)
  def hasAnyInactiveEnrolment(implicit hc: HeaderCarrier): Pure[RuleContext] = Pure(hasAnyInactiveEnrolmentF, HAS_ANY_INACTIVE_ENROLMENT)

}

