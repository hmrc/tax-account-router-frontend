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

import engine.dsl._
import engine.{Rule, RuleEngine}
import javax.inject.{Inject, Singleton}
import model.Conditions
import model.Locations._
import model.{Location, RuleContext}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class TarRules @Inject()(conditions: Conditions) extends RuleEngine {

  override lazy val defaultLocation: Location = BusinessTaxAccount

  override val defaultRuleName = "bta-home-page-passed-through"

  override def rules(implicit request: Request[AnyContent], hc: HeaderCarrier): List[Rule[RuleContext]] = List(

    when(conditions.loggedInViaVerify) thenReturn PersonalTaxAccount withName "pta-home-page-for-verify-user",

    when(conditions.isAGovernmentGatewayUser and not(conditions.ggEnrolmentsAvailable)) thenReturn BusinessTaxAccount withName "bta-home-page-gg-unavailable",

    when(conditions.isAGovernmentGatewayUser and conditions.hasAnyBusinessEnrolment) thenReturn BusinessTaxAccount withName "bta-home-page-for-user-with-business-enrolments",

    when(conditions.isAGovernmentGatewayUser and conditions.hasSaEnrolments and not(conditions.saReturnAvailable)) thenReturn BusinessTaxAccount withName "bta-home-page-sa-unavailable",

    when(conditions.isAGovernmentGatewayUser and conditions.hasSaEnrolments and not(conditions.hasPreviousReturns)) thenReturn BusinessTaxAccount withName "bta-home-page-for-user-with-no-previous-return",

    when(conditions.isAGovernmentGatewayUser and conditions.hasSaEnrolments and (conditions.isInAPartnership or conditions.isSelfEmployed)) thenReturn BusinessTaxAccount withName "bta-home-page-for-user-with-partnership-or-self-employment",

    when(conditions.isAGovernmentGatewayUser and conditions.hasSaEnrolments and not(conditions.isInAPartnership) and not(conditions.isSelfEmployed) and not(conditions.hasNino)) thenReturn BusinessTaxAccount withName "bta-home-page-for-user-with-no-partnership-and-no-self-employment-and-no-nino",

    when(conditions.isAGovernmentGatewayUser and conditions.hasSaEnrolments and not(conditions.isInAPartnership) and not(conditions.isSelfEmployed)) thenReturn PersonalTaxAccount withName "pta-home-page-for-user-with-no-partnership-and-no-self-employment",

    when(not(conditions.hasAnyInactiveEnrolment) and not(conditions.affinityGroupAvailable)) thenReturn BusinessTaxAccount withName "bta-home-page-affinity-group-unavailable",

    when(not(conditions.hasAnyInactiveEnrolment) and conditions.hasIndividualAffinityGroup) thenReturn PersonalTaxAccount withName "pta-home-page-individual"
  )
}
