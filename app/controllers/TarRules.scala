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

package controllers

import engine.Condition._
import engine.RuleEngine
import model.Locations._
import model._

object TarRules extends RuleEngine {

  override val rules = List(
    when(LoggedInViaVerify) thenGoTo PersonalTaxAccount withName "pta-home-page-for-verify-user",

    when(IsAGovernmentGatewayUser and not(GGEnrolmentsAvailable)) thenGoTo BusinessTaxAccount withName "bta-home-page-gg-unavailable",

    when(IsAGovernmentGatewayUser and HasAnyBusinessEnrolment) thenGoTo BusinessTaxAccount withName "bta-home-page-for-user-with-business-enrolments",

    when(IsAGovernmentGatewayUser and HasEnrolments(SA) and not(SAReturnAvailable)) thenGoTo BusinessTaxAccount withName "bta-home-page-sa-unavailable",

    when(IsAGovernmentGatewayUser and HasEnrolments(SA) and not(HasPreviousReturns)) thenGoTo BusinessTaxAccount withName "bta-home-page-for-user-with-no-previous-return",

    when(IsAGovernmentGatewayUser and HasEnrolments(SA) and (IsInAPartnership or IsSelfEmployed)) thenGoTo BusinessTaxAccount withName "bta-home-page-for-user-with-partnership-or-self-employment",

    when(IsAGovernmentGatewayUser and HasEnrolments(SA) and not(IsInAPartnership) and not(IsSelfEmployed) and not(HasNino)) thenGoTo BusinessTaxAccount withName "bta-home-page-for-user-with-no-partnership-and-no-self-employment-and-no-nino",

    when(IsAGovernmentGatewayUser and HasEnrolments(SA) and not(IsInAPartnership) and not(IsSelfEmployed)) thenGoTo PersonalTaxAccount withName "pta-home-page-for-user-with-no-partnership-and-no-self-employment",

    when(not(HasAnyInactiveEnrolment) and not(AffinityGroupAvailable)) thenGoTo BusinessTaxAccount withName "bta-home-page-affinity-group-unavailable",

    when(not(HasAnyInactiveEnrolment) and HasIndividualAffinityGroup) thenGoTo PersonalTaxAccount withName "pta-home-page-individual",

    when(AnyOtherRuleApplied) thenGoTo BusinessTaxAccount withName "bta-home-page-passed-through"
  )
}
