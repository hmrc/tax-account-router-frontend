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

import model.RoutingReason.RoutingReason
import model.{RuleContext, TAuditContext}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future
import scala.util.Success

object Condition {
  def not(condition: Condition): Condition = new CompositeCondition {
    override def evaluate(ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
      condition.evaluate(ruleContext, auditContext).map(!_)
  }

  def when(condition: Condition): When = When(condition)
}

trait Condition {

  self =>

  val auditType: Option[RoutingReason]

  def isTrue(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean]

  def evaluate(ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
    this.isTrue(ruleContext).andThen { case Success(result) if auditType.isDefined => auditContext.setRoutingReason(auditType.get, result) }
  }

  def and(other: Condition): Condition = new CompositeCondition {

    override def evaluate(ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
      val selfEvaluationResult = self.evaluate(ruleContext, auditContext)
      selfEvaluationResult.flatMap(c1r => if (c1r) other.evaluate(ruleContext, auditContext).map(c2r => c1r && c2r) else selfEvaluationResult)
    }
  }

  def or(other: Condition): Condition = new CompositeCondition {

    override def evaluate(ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
      val selfEvaluationResult = self.evaluate(ruleContext, auditContext)
      selfEvaluationResult.flatMap(c1r => if (c1r) selfEvaluationResult else other.evaluate(ruleContext, auditContext))
    }
  }
}
