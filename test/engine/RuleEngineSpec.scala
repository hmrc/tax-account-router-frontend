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

import engine.RoutingReason.{Reason, RoutingReason}
import model.{Location, RuleContext}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import support.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class RuleEngineSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite with ScalaFutures {

  "rule engine" should {

    "return location from rule when a rule is matched" in new Setup {

      implicit lazy val ruleContext: RuleContext = mock[RuleContext]

      val result: (AuditInfo, Location) = ruleEngineStubReturningLocation1.getLocation(ruleContext).run.futureValue

      val (auditInfo, location) = result

      val evaluatedReasons: Map[RoutingReason, Option[Boolean]] =
        auditInfo.routingReasons.filter { case (_, evaluationResult)  => evaluationResult.isDefined }

      evaluatedReasons shouldBe Map(Reason("alwaysTrue") -> Some(true))
      location shouldBe location1
    }

    "return default location when no rules are matched" in new Setup {

      implicit lazy val ruleContext: RuleContext = mock[RuleContext]

      val result: (AuditInfo, Location) = ruleEngineStubReturningLocation2.getLocation(ruleContext).run.futureValue

      val (auditInfo, location) = result

      val evaluatedReasons: Map[RoutingReason, Option[Boolean]] =
        auditInfo.routingReasons.filter { case (_, evaluationResult)  => evaluationResult.isDefined }

      evaluatedReasons shouldBe Map(Reason("alwaysFalse") -> Some(false))

      location shouldBe testDefaultLocation
    }
  }

  trait Setup {

    implicit val fakeRequest: Request[AnyContent] = FakeRequest()
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(fakeRequest.headers)
    implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

    val testDefaultLocation: Location = Location("default-location", "/default-location")
    val location1: Location = Location("location1", "/location1")
    val location2: Location = Location("location2", "/location2")

    type ConditionPredicate = RuleContext => Future[Boolean]
    val alwaysTrueF: ConditionPredicate = _ => Future.successful(true)
    val alwaysFalseF: ConditionPredicate = _ => Future.successful(false)

    val alwaysTrueCondition: Pure[RuleContext] = Pure(alwaysTrueF, Reason("alwaysTrue"))
    val alwaysFalseCondition: Pure[RuleContext] = Pure(alwaysFalseF, Reason("alwaysFalse"))

    val ruleEngineStubReturningLocation1: RuleEngine = new RuleEngine {
      override val defaultLocation: Location = testDefaultLocation
      override def rules(implicit request: Request[AnyContent], hc: HeaderCarrier): List[Rule[RuleContext]] = {
        import engine.dsl._
        List(
          when(alwaysTrueCondition) thenReturn location1 withName "always-true-rule"
        )
      }

      override def defaultRuleName: String = "test1"
    }

    val ruleEngineStubReturningLocation2: RuleEngine = new RuleEngine {
      override val defaultLocation: Location = testDefaultLocation
      override def rules(implicit request: Request[AnyContent], hc: HeaderCarrier): List[Rule[RuleContext]] = {
        import engine.dsl._
        List(
          when(alwaysFalseCondition) thenReturn location2 withName "always-false-rule"
        )
      }

      override def defaultRuleName: String = "test2"
    }
  }
}
