/*
 * Copyright 2019 HM Revenue & Customs
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

import engine.RoutingReason.{Reason, RoutingReason}
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ConditionSpec extends UnitSpec with Matchers with ScalaFutures {

  "or condition" should {
    "return false and evaluate both sides if both sides are false" in new Setup {

      val orCondition = Or(alwaysFalseCondition1, alwaysFalseCondition2)

      val (auditInfo, result) = orCondition.evaluate(context).run.futureValue

      result shouldBe false
      auditInfo.routingReasons shouldBe Map[RoutingReason, Option[Boolean]](
        Reason("alwaysFalse1") -> Some(false),
        Reason("alwaysFalse2") -> Some(false)
      )
    }

    "return true and not evaluate right hand side if left hand side is true" in new Setup {

      val orCondition = Or(alwaysTrueCondition1, alwaysTrueCondition2)

      val (auditInfo, result) = orCondition.evaluate(context).run.futureValue

      result shouldBe true
      auditInfo.routingReasons shouldBe Map[RoutingReason, Option[Boolean]](
        Reason("alwaysTrue1") -> Some(true)
      )
    }

    "return true and evaluate both sides if left hand side is false" in new Setup {

      val orCondition = Or(alwaysFalseCondition1, alwaysTrueCondition1)

      val (auditInfo, result) = orCondition.evaluate(context).run.futureValue

      result shouldBe true
      auditInfo.routingReasons shouldBe Map[RoutingReason, Option[Boolean]](
        Reason("alwaysFalse1") -> Some(false),
        Reason("alwaysTrue1") -> Some(true)
      )
    }
  }

  "and condition" should {
    "return false and evaluate only the left sides if the left side is false" in new Setup {

      val andCondition = And(alwaysFalseCondition1, alwaysFalseCondition2)

      val (auditInfo, result) = andCondition.evaluate(context).run.futureValue

      result shouldBe false
      auditInfo.routingReasons shouldBe Map[RoutingReason, Option[Boolean]](
        Reason("alwaysFalse1") -> Some(false)
      )
    }

    "return false and evaluate right hand side if left hand side is true" in new Setup {

      val andCondition = And(alwaysTrueCondition1, alwaysFalseCondition1)

      val (auditInfo, result) = andCondition.evaluate(context).run.futureValue

      result shouldBe false
      auditInfo.routingReasons shouldBe Map[RoutingReason, Option[Boolean]](
        Reason("alwaysTrue1") -> Some(true),
        Reason("alwaysFalse1") -> Some(false)
      )
    }

    "return true and evaluate both sides if left hand side is true" in new Setup {

      val andCondition = And(alwaysTrueCondition1, alwaysTrueCondition2)

      val (auditInfo, result) = andCondition.evaluate(context).run.futureValue

      result shouldBe true
      auditInfo.routingReasons shouldBe Map[RoutingReason, Option[Boolean]](
        Reason("alwaysTrue1") -> Some(true),
        Reason("alwaysTrue2") -> Some(true)
      )
    }
  }

  "not condition" should {
    "return true if the condition returned false" in new Setup {

      val notCondition = Not(alwaysTrueCondition1)

      val (auditInfo, result) = notCondition.evaluate(context).run.futureValue

      result shouldBe false
      auditInfo.routingReasons shouldBe Map[RoutingReason, Option[Boolean]](
        Reason("alwaysTrue1") -> Some(true)
      )
    }

    "return false if the condition returned true" in new Setup {

      val notCondition = Not(alwaysFalseCondition1)

      val (auditInfo, result) = notCondition.evaluate(context).run.futureValue

      result shouldBe true
      auditInfo.routingReasons shouldBe Map[RoutingReason, Option[Boolean]](
        Reason("alwaysFalse1") -> Some(false)
      )
    }
  }

  trait Setup {
    val context = ""

    type ConditionPredicate = (String) => Future[Boolean]
    val alwaysTrueF: ConditionPredicate = rc => Future.successful(true)
    val alwaysFalseF: ConditionPredicate = rc => Future.successful(false)

    val alwaysTrueCondition1 = Pure(alwaysTrueF, Reason("alwaysTrue1"))
    val alwaysTrueCondition2 = Pure(alwaysTrueF, Reason("alwaysTrue2"))
    val alwaysFalseCondition1 = Pure(alwaysFalseF, Reason("alwaysFalse1"))
    val alwaysFalseCondition2 = Pure(alwaysFalseF, Reason("alwaysFalse2"))
  }
}
