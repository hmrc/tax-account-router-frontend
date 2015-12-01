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

import model.AuditContext._
import model.Location.LocationType
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
  val HAS_PRINT_PREFERENCES_ALREADY_SET = Reason("has-print-preferences-already-set")
  val HAS_BUSINESS_ENROLMENTS = Reason("has-business-enrolments")
  val HAS_PREVIOUS_RETURNS = Reason("has-previous-returns")
  val IS_IN_A_PARTNERSHIP = Reason("is-in-a-partnership")
  val IS_SELF_EMPLOYED = Reason("is-self-employed")
  val HAS_SA_ENROLMENTS = Reason("has-self-assessment-enrolments")
}

import model.RoutingReason._

object AuditContext {

  def defaultRoutingReasons = mutableMap[String, String](
    IS_A_VERIFY_USER.key -> "-" ,
    IS_A_GOVERNMENT_GATEWAY_USER.key -> "-" ,
    HAS_PRINT_PREFERENCES_ALREADY_SET.key -> "-" ,
    HAS_BUSINESS_ENROLMENTS.key -> "-" ,
    HAS_PREVIOUS_RETURNS.key -> "-" ,
    IS_IN_A_PARTNERSHIP.key -> "-" ,
    IS_SELF_EMPLOYED.key -> "-",
    HAS_SA_ENROLMENTS.key -> "-"
  )
}

trait TAuditContext {

  private val routingReasons: mutableMap[String, String] = defaultRoutingReasons
  private val throttlingDetails: mutableMap[String, String] = mutableMap.empty

  private val transactionNames: Map[LocationType, String] = Map(
    Location.PersonalTaxAccount -> "sent to personal tax account",
    Location.BusinessTaxAccount -> "sent to business tax account"
  )

  var ruleApplied: String = ""

  def getReasons: mutableMap[String, String] = routingReasons

  def getThrottlingDetails: mutableMap[String, String] = throttlingDetails

  def setThrottlingDetails(throttlingAuditContext: ThrottlingAuditContext): Unit =
    throttlingDetails +=(
      "enabled" -> throttlingAuditContext.throttlingEnabled.toString,
      "following-previously-routed-destination" -> throttlingAuditContext.followingPreviouslyRoutedDestination.toString,
      "percentage" -> throttlingAuditContext.throttlingPercentage.getOrElse("-").toString,
      "throttled" -> throttlingAuditContext.throttled.toString,
      "destination-url-before-throttling" -> throttlingAuditContext.initialDestination.url,
      "destination-name-before-throttling" -> throttlingAuditContext.initialDestination.name
      )

  def setRoutingReason(auditEventType: RoutingReason, result: Boolean)(implicit ec: ExecutionContext): Unit =
    routingReasons += (auditEventType.key -> result.toString)

  def toAuditEvent(location: LocationType)(implicit hc: HeaderCarrier, authContext: AuthContext, request: Request[AnyContent]): Future[ExtendedDataEvent] = {
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

case class ThrottlingAuditContext(throttlingPercentage: Option[Int], throttled: Boolean, initialDestination: LocationType, throttlingEnabled: Boolean, followingPreviouslyRoutedDestination: Boolean)