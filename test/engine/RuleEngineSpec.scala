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

import engine.RoutingReason.Reason
import model.{Location, _}
import org.mockito.Matchers.{eq => eqTo}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class RuleEngineSpec extends UnitSpec with MockitoSugar with WithFakeApplication with ScalaFutures {

  "rule engine" should {

    "rule matched" in new Setup {
      implicit lazy val fakeRequest = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      implicit lazy val ruleContext = RuleContext(None)(fakeRequest, hc)

      val result = ruleEngineStubReturningLocation1.getLocation(ruleContext).run.futureValue

      val (auditInfo, location) = result

      val evaluatedReasons = auditInfo.routingReasons.filter { case (reason, evaluationResult)  => evaluationResult.isDefined }

      evaluatedReasons shouldBe Map(Reason("alwaysTrue") -> Some(true))
      location shouldBe location1
    }

    "rule not matched" in new Setup {
      implicit lazy val fakeRequest = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

      implicit lazy val ruleContext = RuleContext(None)(fakeRequest, hc)

      val result = ruleEngineStubReturningLocation2.getLocation(ruleContext).run.futureValue

      val (auditInfo, location) = result

      val evaluatedReasons = auditInfo.routingReasons.filter { case (reason, evaluationResult)  => evaluationResult.isDefined }

      evaluatedReasons shouldBe Map(Reason("alwaysFalse") -> Some(false))

      location shouldBe testDefaultLocation
    }
  }

  trait Setup {

    val testDefaultLocation = Location("default-location", "/default-location")
    val location1 = Location("location1", "/location1")
    val location2 = Location("location2", "/location2")

    type ConditionPredicate = (RuleContext) => Future[Boolean]
    val alwaysTrueF: ConditionPredicate = rc => Future.successful(true)
    val alwaysFalseF: ConditionPredicate = rc => Future.successful(false)

    val alwaysTrueCondition = Pure(alwaysTrueF, Reason("alwaysTrue"))
    val alwaysFalseCondition = Pure(alwaysFalseF, Reason("alwaysFalse"))

    val ruleEngineStubReturningLocation1 = new RuleEngine {
      override val defaultLocation: Location = testDefaultLocation
      override val rules: List[Rule[RuleContext]] = {
        import engine.dsl._
        List(
          when(alwaysTrueCondition) thenReturn location1 withName "always-true-rule"
        )
      }

      override def defaultRuleName: String = "test1"
    }

    val ruleEngineStubReturningLocation2 = new RuleEngine {
      override val defaultLocation: Location = testDefaultLocation
      override val rules: List[Rule[RuleContext]] = {
        import engine.dsl._
        List(
          when(alwaysFalseCondition) thenReturn location2 withName "always-false-rule"
        )
      }

      override def defaultRuleName: String = "test2"
    }
  }
}
