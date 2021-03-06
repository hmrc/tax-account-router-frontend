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

package engine

import cats.Semigroup
import engine.RoutingReason._
import model.{Location, Locations}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

sealed trait TAuditInfo {
  lazy val transactionNames = Map(
    Locations.PersonalTaxAccount -> "sent to personal tax account",
    Locations.BusinessTaxAccount -> "sent to business tax account"
  )

  def toAuditEvent(location: Location)(implicit hc: HeaderCarrier, enrolmentsContext: Enrolments, request: Request[AnyContent]): ExtendedDataEvent = {
    this match {
      case AuditInfo(routingReasons, ruleApplied, throttlingInfo) =>
        val enrolments: Set[Enrolment] = enrolmentsContext.enrolments
        val optionalAccounts: JsObject = Json.obj("enrolments" -> Json.toJson[Set[Enrolment]](enrolments))
        ExtendedDataEvent(
          auditSource = "tax-account-router-frontend",
          auditType = "Routing",
          tags = hc.toAuditTags(transactionNames.getOrElse(location, "unknown transaction"), request.path),
          detail = Json.obj(
            "destination" -> location.url,
            "reasons" -> routingReasons.map {
              case (k, None) => k.key -> "-"
              case (k, Some(v)) => k.key -> v.toString
            },
            "throttling" -> throttlingInfo.map(info => Map(
              "enabled" -> info.throttlingEnabled.toString,
              "percentage" -> info.percentage.map(_.toString).getOrElse("-"),
              "throttled" -> info.throttled.toString,
              "destination-url-before-throttling" -> info.initialDestination.url,
              "destination-name-before-throttling" -> info.initialDestination.name
            )),
            "ruleApplied" -> ruleApplied
          ) ++ optionalAccounts
        )
    }
  }
}

case class ThrottlingInfo(percentage: Option[Int],
                          throttled: Boolean,
                          initialDestination: Location,
                          throttlingEnabled: Boolean)

case class AuditInfo(routingReasons: Map[RoutingReason, Option[Boolean]],
                     ruleApplied: Option[String],
                     throttlingInfo: Option[ThrottlingInfo]) extends TAuditInfo

object AuditInfo {

  val emptyReasons: Map[RoutingReason.Reason, Option[Boolean]] = allReasons.map(reason => reason -> None).toMap

  val Empty: AuditInfo = AuditInfo(routingReasons = emptyReasons, ruleApplied = None, throttlingInfo = None)

  def apply(routingReasons: Map[RoutingReason, Option[Boolean]]): AuditInfo = AuditInfo(routingReasons = routingReasons, ruleApplied = None, throttlingInfo = None)

  implicit val mergeAudit: Semigroup[AuditInfo] = new Semigroup[AuditInfo] {
    override def combine(x: AuditInfo, y: AuditInfo): AuditInfo = x.copy(
      routingReasons = x.routingReasons ++ y.routingReasons,
      ruleApplied = x.ruleApplied orElse y.ruleApplied,
      throttlingInfo = x.throttlingInfo orElse y.throttlingInfo
    )
  }
}
