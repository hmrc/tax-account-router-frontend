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
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.{ExecutionContext, Future}

object AuditEventType {

  type AuditEventType = EventType

  sealed case class EventType(key: String)

  val IS_A_VERIFY_USER = EventType("is-a-verify-user")
  val IS_A_GOVERNMENT_GATEWAY_USER = EventType("is-a-government-gateway-user")
  val HAS_NEVER_SEEN_WELCOME_PAGE_BEFORE = EventType("has-never-seen-welcome-page-before")
  val HAS_PRINT_PREFERENCES_ALREADY_SET = EventType("has-print-preferences-already-set")
  val HAS_BUSINESS_ENROLMENTS = EventType("has-business-enrolments")
  val HAS_PREVIOUS_RETURNS = EventType("has-previous-returns")
  val IS_IN_A_PARTNERSHIP = EventType("is-in-a-partnership")
  val IS_SELF_EMPLOYED = EventType("is-self-employed")
  val HAS_SA_ENROLMENTS = EventType("has-self-assessment-enrolments")
}

import model.AuditEventType._

object AuditContext {

  def defaultReasons = scala.collection.mutable.Map[String, String](
    IS_A_VERIFY_USER.key -> "-" ,
    IS_A_GOVERNMENT_GATEWAY_USER.key -> "-" ,
    HAS_NEVER_SEEN_WELCOME_PAGE_BEFORE.key -> "-" ,
    HAS_PRINT_PREFERENCES_ALREADY_SET.key -> "-" ,
    HAS_BUSINESS_ENROLMENTS.key -> "-" ,
    HAS_PREVIOUS_RETURNS.key -> "-" ,
    IS_IN_A_PARTNERSHIP.key -> "-" ,
    IS_SELF_EMPLOYED.key -> "-",
    HAS_SA_ENROLMENTS.key -> "-"
  )
}

trait TAuditContext {

  private val reasons: scala.collection.mutable.Map[String, String] = defaultReasons
  private val throttling: scala.collection.mutable.Map[String, String] = scala.collection.mutable.Map[String, String]()

  def setValue(throttlingAuditContext: ThrottlingAuditContext): Unit =
    throttling +=(
      "enabled" -> throttlingAuditContext.throttlingEnabled.toString,
      "percentage" -> throttlingAuditContext.throttlingPercentage.getOrElse("-").toString,
      "throttled" -> throttlingAuditContext.throttled.toString,
      "destination-before-throttling" -> throttlingAuditContext.initialDestination.url
      )

  def setValue(auditEventType: AuditEventType, result: Boolean)(implicit ec: ExecutionContext): Unit =
    reasons += (auditEventType.key -> result.toString)

  def toAuditEvent(url: String)(implicit hc: HeaderCarrier, authContext: AuthContext, request: Request[AnyContent]): Future[ExtendedDataEvent] = {
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
        tags = hc.toAuditTags("transaction-name", request.path),
        detail = Json.obj(
          "authId" -> authContext.user.userId,
          "destination" -> url,
          "reasons" -> reasons.toMap[String, String],
          "throttling" -> throttling.toMap[String, String]
        ) ++ optionalAccounts
      )
    }
  }
}

case class AuditContext() extends TAuditContext

case class ThrottlingAuditContext(throttlingPercentage: Option[Float], throttled: Boolean, initialDestination: LocationType, throttlingEnabled: Boolean)