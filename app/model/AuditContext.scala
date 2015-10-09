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
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.config.AppName

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

object AuditEventType extends Enumeration {

  type AuditEventType = EventType

  sealed case class EventType(key: String) extends Val

  val HAS_ALREADY_SEEN_WELCOME_PAGE = EventType("has-already-seen-welcome-page")
  val HAS_PRINT_PREFERENCES_ALREADY_SET = EventType("has-print-preferences-already-set")
  val HAS_BUSINESS_ENROLMENTS = EventType("has-business-enrolments")
  val HAS_PREVIOUS_RETURNS = EventType("has-previous-returns")
  val IS_IN_A_PARTNERSHIP = EventType("is-in-a-partnership")
  val IS_SELF_EMPLOYED = EventType("is-self-employed")
}

import model.AuditEventType._

object AuditContext {

  def defaultReasons = scala.collection.mutable.Map[String, String](
    HAS_ALREADY_SEEN_WELCOME_PAGE.key -> "-" ,
    HAS_PRINT_PREFERENCES_ALREADY_SET.key -> "-" ,
    HAS_BUSINESS_ENROLMENTS.key -> "-" ,
    HAS_PREVIOUS_RETURNS.key -> "-" ,
    IS_IN_A_PARTNERSHIP.key -> "-" ,
    IS_SELF_EMPLOYED.key -> "-"
  )
}

trait TAuditContext {

  private val reasons: scala.collection.mutable.Map[String, String] = defaultReasons

  def setValue[T](auditEventType: AuditEventType, futureResult: Future[T])(implicit ec: ExecutionContext): Future[T] =
    futureResult.andThen { case Success(result) => reasons += (auditEventType.key -> result.toString) }

  def toAuditEvent(url: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): ExtendedDataEvent = ExtendedDataEvent(
    auditSource = AppName.appName,
    auditType = "Routing",
    tags = hc.toAuditTags("transaction-name", request.path),
    detail = Json.obj(
      "destination" -> url,
      "reasons" -> reasons.toMap[String, String]
    )
  )
}

case class AuditContext() extends TAuditContext