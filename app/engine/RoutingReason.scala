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

package engine

object RoutingReason {

  type RoutingReason = Reason

  sealed case class Reason(key: String)

  val IS_A_VERIFY_USER = Reason("is-a-verify-user")
  val IS_A_GOVERNMENT_GATEWAY_USER = Reason("is-a-government-gateway-user")
  val GG_ENROLMENTS_AVAILABLE = Reason("gg-enrolments-available")
  val HAS_BUSINESS_ENROLMENTS = Reason("has-business-enrolments")
  val SA_RETURN_AVAILABLE = Reason("sa-return-available")
  val HAS_PREVIOUS_RETURNS = Reason("has-previous-returns")
  val IS_IN_A_PARTNERSHIP = Reason("is-in-a-partnership")
  val IS_SELF_EMPLOYED = Reason("is-self-employed")
  val HAS_NINO = Reason("has-nino")
  val HAS_INDIVIDUAL_AFFINITY_GROUP = Reason("has-individual-affinity-group")
  val HAS_ANY_INACTIVE_ENROLMENT = Reason("has-any-inactive-enrolment")
  val AFFINITY_GROUP_AVAILABLE = Reason("affinity-group-available")
  val HAS_SA_ENROLMENTS = Reason("has-self-assessment-enrolments")

  val allReasons = List(
    IS_A_VERIFY_USER,
    IS_A_GOVERNMENT_GATEWAY_USER,
    GG_ENROLMENTS_AVAILABLE,
    HAS_BUSINESS_ENROLMENTS,
    SA_RETURN_AVAILABLE,
    HAS_PREVIOUS_RETURNS,
    IS_IN_A_PARTNERSHIP,
    IS_SELF_EMPLOYED,
    HAS_NINO,
    HAS_INDIVIDUAL_AFFINITY_GROUP,
    HAS_ANY_INACTIVE_ENROLMENT,
    AFFINITY_GROUP_AVAILABLE,
    HAS_SA_ENROLMENTS
  )
}
