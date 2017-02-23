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

import cats.data.WriterT
import cats.kernel.Semigroup
import engine.RoutingReason.RoutingReason
import model.Location

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait Expr[Context, Result] {
  def evaluate[A <: AuditInfo: Semigroup](context: Context): WriterT[Future, AuditInfo, Result]
}

case class Pure[C](f: C => Future[Boolean], routingReason: RoutingReason) extends Condition[C] with Reason
case class And[C](c1: Condition[C], c2: Condition[C]) extends Condition[C]
case class Or[C](c1: Condition[C], c2: Condition[C]) extends Condition[C]
case class Not[C](condition: Condition[C]) extends Condition[C]
case class When[C](condition: Condition[C])

sealed trait Condition[C] extends Expr[C, Boolean] {

  def evaluate[A <: AuditInfo: Semigroup](context: C): ConditionResult = {

    val auditInfoSemigroup = implicitly[Semigroup[AuditInfo]]

    this match {

      case Pure(f, auditType) =>
        WriterT {
          f(context)
            .map(isTrue => (AuditInfo(Map(auditType -> Some(isTrue))), isTrue))
        }

      case And(c1, c2) => WriterT {
        c1.evaluate(context).run.flatMap { case (info1, result1) =>
          if (result1) {
            c2.evaluate(context).run.map { case (info2, result2) =>
              (auditInfoSemigroup.combine(info1, info2), result1 && result2)
            }
          }
          else Future.successful(info1, result1)
        }
      }

      case Or(c1, c2) => WriterT {
        c1.evaluate(context).run.flatMap { case (info1, result1) =>
          if (!result1) {
            c2.evaluate(context).run.map { case (info2, result2) =>
              (auditInfoSemigroup.combine(info1, info2), result1 || result2)
            }
          }
          else Future.successful(info1, result1)
        }
      }

      case Not(condition) =>
        for {
          c <- condition.evaluate(context)
        } yield !c
    }
  }
}

object Condition {
  implicit class ConditionOps[C](condition: Condition[C]) {
    def and(other: Condition[C]) = And(condition, other)
    def or(other: Condition[C]) = Or(condition, other)
  }

  def not[C](condition: Condition[C]) = Not(condition)
}

sealed trait Reason { self: Condition[_] =>
  def routingReason: RoutingReason
}

sealed trait Rule[C] extends Expr[C, Option[Location]] {

  def evaluate[A <: AuditInfo: Semigroup](context: C): RuleResult = {

    def go(condition: Condition[C], location: Location, maybeName: Option[String] = None): RuleResult = {
      condition.evaluate(context).mapBoth { case (auditInfo, result) =>
        val maybeLocation: Option[Location] = Option(result).collect { case true => location }

        val info = (for {
          name <- maybeName
          _ <- maybeLocation
        } yield auditInfo.copy(ruleApplied = Some(name))) getOrElse auditInfo

        (info, maybeLocation)
      }
    }

    this match {
      case BaseRule(condition, location) => go(condition, location)
      case RuleWithName(condition, location, name) => go(condition, location, Option(name))
    }
  }
}

object When {
  implicit class WhenOps[C](when: When[C]) {
    def thenReturn(location: Location): Rule[C] = when match {
      case When(condition) => BaseRule(condition, location)
    }
  }
}

object Rule {

  def when[C](condition: Condition[C]) = When(condition)

  implicit class RuleOps[C](rule: Rule[C]) {
    def withName(name: String): Rule[C] = rule match {
      case BaseRule(condition, location) => RuleWithName(condition, location, name)
      case r@RuleWithName(_, _, _) => r.copy(name = name)
    }
  }
}

private case class BaseRule[C](condition: Condition[C], location: Location) extends Rule[C]
private case class RuleWithName[C](condition: Condition[C], location: Location, name: String) extends Rule[C] with Name

sealed trait Name { self: Rule[_] =>
  def name: String
}