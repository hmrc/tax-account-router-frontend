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
import uk.gov.hmrc.play.audit.model.{AuditEvent, ExtendedDataEvent}
import uk.gov.hmrc.play.config.AppName

import scala.concurrent.{ExecutionContext, Future}


object AuditContext {

  val has_seen_welcome_page: String = "has-seen-welcome-page"
  val has_print_preferences_set: String = "has-print-preferences-set"
  val has_business_enrolments: String = "has-business-enrolments"
  val has_previous_returns: String = "has-previous-returns"
  val is_in_a_partnership: String = "is-in-a-partnership"
  val is_self_employed: String = "is-self-employed"

  def defaultReasons = scala.collection.mutable.Map[String, String](
    has_seen_welcome_page -> "-" ,
    has_print_preferences_set -> "-" ,
    has_business_enrolments -> "-" ,
    has_previous_returns -> "-" ,
    is_in_a_partnership -> "-" ,
    is_self_employed -> "-"
  )
}

trait TAuditContext {

  protected def reasons: scala.collection.mutable.Map[String, String] = defaultReasons

  def setHasSeenWelcomePage(value: Future[Boolean])(implicit ec: ExecutionContext) = {
    setValue(has_seen_welcome_page, value)
  }

  def setHasPrintPreferencesSet(value: Future[Boolean])(implicit ec: ExecutionContext) = {
    setValue(has_print_preferences_set, value)
  }

  def setHasBusinessEnrolments(value: Future[Boolean])(implicit ec: ExecutionContext) = {
    setValue(has_business_enrolments, value)
  }

  def setHasPreviousReturns(value: Future[Boolean])(implicit ec: ExecutionContext) = {
    setValue(has_previous_returns, value)
  }

  def setIsInAPartnership(value: Future[Boolean])(implicit ec: ExecutionContext) = {
    setValue(is_in_a_partnership, value)
  }

  def setIsSelfEmployed(value: Future[Boolean])(implicit ec: ExecutionContext) = {
    setValue(is_self_employed, value)
  }

  def setValue(key: String, futureResult: Future[Boolean])(implicit ec: ExecutionContext): Future[Boolean] = {
    futureResult.andThen { case result => reasons += (key -> result.toString) }
  }

  def toAuditEvent(url: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): AuditEvent = ExtendedDataEvent(
    auditSource = AppName.appName,
    auditType = "Routing",
    tags = hc.toAuditTags("transaction-name", request.path),
    detail = Json.obj(
      "destination" -> url,
      "reasons" -> reasons.toMap[String, String]
    )
  )
}

case class AuditContext(override val reasons: scala.collection.mutable.Map[String, String] = defaultReasons) extends TAuditContext