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

import cats.data.WriterT
import cats.{FlatMap, Functor, Semigroup}
import engine.RoutingReason.{RoutingReason, allReasons}
import model.{Location, Locations}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

package object engine {

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

  sealed trait TAuditInfo {
    lazy val transactionNames = Map(
      Locations.PersonalTaxAccount -> "sent to personal tax account",
      Locations.BusinessTaxAccount -> "sent to business tax account"
    )

    def toAuditEvent(location: Location)(implicit hc: HeaderCarrier, authContext: AuthContext, request: Request[AnyContent]): ExtendedDataEvent = {
      this match {
        case AuditInfo(routingReasons, ruleApplied, throttlingInfo) =>
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
              "destination" -> location.url,
              "reasons" -> routingReasons.map {
                case (k, None) => k.key -> "-"
                case (k, Some(v)) => k.key -> v.toString
              },
              "throttling" -> throttlingInfo.map(info => Map(
                "enabled" -> info.throttlingEnabled.toString,
                "sticky-routing-applied" -> info.stickyRoutingApplied.toString,
                "throttlingPercentage" -> info.throttlingPercentage.map(_.toString).getOrElse("-"),
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

  case class AuditInfo(routingReasons: Map[RoutingReason, Option[Boolean]],
                       ruleApplied: Option[String],
                       throttlingInfo: Option[ThrottlingInfo]) extends TAuditInfo

  case class ThrottlingInfo(throttlingPercentage: Option[Int],
                            throttled: Boolean,
                            initialDestination: Location,
                            throttlingEnabled: Boolean,
                            stickyRoutingApplied: Boolean)

  object AuditInfo {

    val emptyReasons: Map[RoutingReason.Reason, Option[Boolean]] = allReasons.map(reason => reason -> None).toMap

    val Empty = AuditInfo(routingReasons = emptyReasons, ruleApplied = None, throttlingInfo = None)

    def apply(routingReasons: Map[RoutingReason, Option[Boolean]]): AuditInfo = AuditInfo(routingReasons = routingReasons, ruleApplied = None, throttlingInfo = None)

    implicit val mergeAudit: Semigroup[AuditInfo] = new Semigroup[AuditInfo] {
      override def combine(x: AuditInfo, y: AuditInfo): AuditInfo = x.copy(
        routingReasons = x.routingReasons ++ y.routingReasons,
        ruleApplied = x.ruleApplied orElse y.ruleApplied
      )
    }
  }

  implicit def futureFunctor(implicit ec: ExecutionContext): Functor[Future] = new Functor[Future] {
    override def map[A, B](fa: Future[A])(f: (A) => B): Future[B] = fa.map(f)
  }

  implicit def futureFlatMap(implicit ec: ExecutionContext): FlatMap[Future] = new FlatMap[Future] {
    override def flatMap[A, B](fa: Future[A])(f: (A) => Future[B]): Future[B] = fa flatMap f

    override def tailRecM[A, B](a: A)(f: (A) => Future[Either[A, B]]): Future[B] = f(a).map {
      case Right(v) => v
    }

    override def map[A, B](fa: Future[A])(f: (A) => B): Future[B] = fa map f
  }

  type ConditionResult = WriterT[Future, AuditInfo, Boolean]
  type RuleResult = WriterT[Future, AuditInfo, Option[Location]]
  type EngineResult = WriterT[Future, AuditInfo, Location]
  val emptyRuleResult: RuleResult = WriterT(Future.successful(AuditInfo.Empty, None))

}
