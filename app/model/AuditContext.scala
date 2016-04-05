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

import model.AuditContext._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.collection.mutable.{Map => mutableMap}
import scala.concurrent.{ExecutionContext, Future}

object RoutingReason {

  type RoutingReason = Reason

  sealed case class Reason(key: String)

  val IS_A_VERIFY_USER = Reason("is-a-verify-user")
  val IS_A_GOVERNMENT_GATEWAY_USER = Reason("is-a-government-gateway-user")
  val GG_ENROLMENTS_AVAILABLE = Reason("gg-enrolments-available")
  val HAS_BUSINESS_ENROLMENTS = Reason("has-business-enrolments")
  val HAS_SA_ENROLMENTS = Reason("has-self-assessment-enrolments")
  val SA_RETURN_AVAILABLE = Reason("sa-return-available")
  val HAS_PREVIOUS_RETURNS = Reason("has-previous-returns")
  val IS_IN_A_PARTNERSHIP = Reason("is-in-a-partnership")
  val IS_SELF_EMPLOYED = Reason("is-self-employed")
  val HAS_NINO = Reason("has-nino")
  val HAS_SA_UTR = Reason("has-sa-utr")
  val HAS_REGISTERED_FOR_2SV = Reason("has-registered-for-2sv")
  val HAS_STRONG_CREDENTIALS = Reason("has-strong-credentials")
  val HAS_ONLY_ONE_ENROLMENT = Reason("has-only-one-enrolment")
  val HAS_INDIVIDUAL_AFFINITY_GROUP = Reason("has-individual-affinity-group")
  val HAS_ANY_INACTIVE_ENROLMENT = Reason("has-any-inactive-enrolment")

  val allReasons = List(
    IS_A_VERIFY_USER,
    IS_A_GOVERNMENT_GATEWAY_USER,
    GG_ENROLMENTS_AVAILABLE,
    HAS_BUSINESS_ENROLMENTS,
    HAS_SA_ENROLMENTS,
    SA_RETURN_AVAILABLE,
    HAS_PREVIOUS_RETURNS,
    IS_IN_A_PARTNERSHIP,
    IS_SELF_EMPLOYED,
    HAS_NINO,
    HAS_SA_UTR,
    HAS_REGISTERED_FOR_2SV,
    HAS_STRONG_CREDENTIALS,
    HAS_ONLY_ONE_ENROLMENT,
    HAS_INDIVIDUAL_AFFINITY_GROUP
  )
}

import model.RoutingReason._

object AuditContext {

  def defaultRoutingReasons = mutableMap(allReasons.map(reason => reason.key -> "-"): _*)
}

trait TAuditContext {

  private val routingReasons: mutableMap[String, String] = defaultRoutingReasons
  private val throttlingDetails: mutableMap[String, String] = mutableMap.empty
  var sentTo2SVRegister = false

  private lazy val transactionNames = Map(
    Locations.PersonalTaxAccount -> "sent to personal tax account",
    Locations.BusinessTaxAccount -> "sent to business tax account"
  )

  var ruleApplied = ""

  def getReasons: mutableMap[String, String] = routingReasons

  def getThrottlingDetails: mutableMap[String, String] = throttlingDetails

  def setThrottlingDetails(throttlingAuditContext: ThrottlingAuditContext): Unit =
    throttlingDetails +=(
      "enabled" -> throttlingAuditContext.throttlingEnabled.toString,
      "sticky-routing-applied" -> throttlingAuditContext.stickyRoutingApplied.toString,
      "percentage" -> throttlingAuditContext.throttlingPercentage.getOrElse("-").toString,
      "throttled" -> throttlingAuditContext.throttled.toString,
      "destination-url-before-throttling" -> throttlingAuditContext.initialDestination.url,
      "destination-name-before-throttling" -> throttlingAuditContext.initialDestination.name
      )

  def setRoutingReason(auditEventType: RoutingReason, result: Boolean)(implicit ec: ExecutionContext): Unit =
    routingReasons += (auditEventType.key -> result.toString)

  def toAuditEvent(location: Location)(implicit hc: HeaderCarrier, authContext: AuthContext, request: Request[AnyContent]): Future[ExtendedDataEvent] = {
    Future {
      val accounts: Accounts = authContext.principal.accounts
      val accountMap = accounts.toMap
      val accountsAsJson: Seq[(String, JsValueWrapper)] = accountMap
        .map { case (k, v) => (k, Json.toJsFieldJsValueWrapper(v.toString)) }
        .toSeq
      val optionalAccounts: JsObject = Json.obj(accountsAsJson: _*)
      ExtendedDataEvent(
        auditSource = AppName.appName,
        auditType = "Routing",
        tags = hc.toAuditTags(transactionNames.getOrElse(location, "unknown transaction"), request.path),
        detail = Json.obj(
          "authId" -> authContext.user.userId,
          "destination" -> location.url,
          "reasons" -> routingReasons.toMap[String, String],
          "throttling" -> throttlingDetails.toMap[String, String],
          "ruleApplied" -> ruleApplied
        ) ++ optionalAccounts
      )
    }
  }
}

case class AuditContext() extends TAuditContext

case class ThrottlingAuditContext(throttlingPercentage: Option[Int], throttled: Boolean, initialDestination: Location, throttlingEnabled: Boolean, stickyRoutingApplied: Boolean)
