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

package engine

import helpers.SpecHelpers
import model.Location.LocationType
import model.RoutingReason._
import model.{RoutingReason, _}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.mockito.verification.VerificationMode
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ConditionSpec extends UnitSpec with MockitoSugar with Eventually with SpecHelpers {

  implicit val fakeRequest = FakeRequest()
  implicit val hc = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

  "a Condition" should {

    val scenarios = Table(
      ("scenario", "auditTypeDefined", "expectedAuditContextInteractions"),
      ("audit type is defined", true, times(1)),
      ("audit type is not defined", false, never())
    )

    forAll(scenarios) { (scenario: String, auditTypeDefined: Boolean, expectedAuditContextInteractions: VerificationMode) =>

      s"return and eventually audit its own truth when evaluated - scenario: $scenario" in {
        val expectedResult = true

        val auditEventType = RoutingReason.Reason("event-key")

        val condition = new Condition {
          override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(expectedResult)

          override val auditType: Option[RoutingReason] = if (auditTypeDefined) Some(auditEventType) else None
        }

        val mockAuthContext = mock[AuthContext]
        val mockRuleContext = mock[RuleContext]
        val mockAuditContext = mock[TAuditContext]

        val result = await(condition.evaluate(mockAuthContext, mockRuleContext, mockAuditContext))
        result shouldBe expectedResult

        eventually {
          verify(mockAuditContext, expectedAuditContextInteractions).setRoutingReason(eqTo(auditEventType), eqTo(true))(any[ExecutionContext])
        }
      }
    }
  }

  it should {

    val scenarios = Table(
      ("scenario", "condition1Truth", "condition2Truth", "expectedResultConditionTruth"),
      ("true and true", true, true, true),
      ("true and false", true, false, false),
      ("false and true", false, true, false),
      ("false and false", false, false, false)
    )

    forAll(scenarios) { (scenario: String, condition1Truth: Boolean, condition2Truth: Boolean, expectedResultConditionTruth: Boolean) =>

      s"be combined with another condition using 'and' operator - scenario: $scenario" in {

        val mockAuthContext = mock[AuthContext]
        val mockRuleContext = mock[RuleContext]
        val mockAuditContext = mock[TAuditContext]

        val condition1 = new Condition {
          override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(condition1Truth)

          override val auditType: Option[RoutingReason] = None
        }

        val condition2 = mock[Condition]
        when(condition2.evaluate(eqTo(mockAuthContext), eqTo(mockRuleContext), eqTo(mockAuditContext))(eqTo(fakeRequest), eqTo(hc))).thenReturn(Future(condition2Truth))

        val resultCondition: Condition = condition1.and(condition2)

        val resultConditionTruth: Boolean = await(resultCondition.evaluate(mockAuthContext, mockRuleContext, mockAuditContext))

        resultConditionTruth shouldBe expectedResultConditionTruth

        if (!condition1Truth) verify(condition2, never()).evaluate(any[AuthContext], any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])
      }
    }
  }

  it should {
    val scenarios = Table(
      ("scenario", "condition1Truth", "condition2Truth", "expectedResultConditionTruth"),
      ("true and true", true, true, true),
      ("true and false", true, false, true),
      ("false and true", false, true, true),
      ("false and false", false, false, false)
    )

    forAll(scenarios) { (scenario: String, condition1Truth: Boolean, condition2Truth: Boolean, expectedResultConditionTruth: Boolean) =>

      s"be combined with another condition using 'or' operator - scenario: $scenario" in {

        val mockAuthContext = mock[AuthContext]
        val mockRuleContext = mock[RuleContext]
        val mockAuditContext = mock[TAuditContext]

        val condition1 = new Condition {
          override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(condition1Truth)

          override val auditType: Option[RoutingReason] = None
        }

        val condition2 = mock[Condition]
        when(condition2.evaluate(eqTo(mockAuthContext), eqTo(mockRuleContext), eqTo(mockAuditContext))(eqTo(fakeRequest), eqTo(hc))).thenReturn(Future(condition2Truth))

        val resultCondition: Condition = condition1.or(condition2)

        val resultConditionTruth: Boolean = await(resultCondition.evaluate(mockAuthContext, mockRuleContext, mockAuditContext))

        resultConditionTruth shouldBe expectedResultConditionTruth

        if (condition1Truth) verify(condition2, never()).evaluate(any[AuthContext], any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])
      }
    }
  }

  it should {

    val scenarios = Table(
      ("scenario", "conditionTruth", "expectedResultConditionTruth"),
      ("not true", true, false),
      ("not false", false, true)
    )

    forAll(scenarios) { (scenario: String, conditionTruth: Boolean, expectedResultConditionTruth: Boolean) =>
      s"be negated - scenario: $scenario" in {

        val mockAuthContext = mock[AuthContext]
        val mockRuleContext = mock[RuleContext]
        val mockAuditContext = mock[TAuditContext]

        val condition = new Condition {
          override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(conditionTruth)

          override val auditType: Option[RoutingReason] = None
        }

        val resultCondition: Condition = Condition.not(condition)

        val resultConditionTruth: Boolean = await(resultCondition.evaluate(mockAuthContext, mockRuleContext, mockAuditContext))

        resultConditionTruth shouldBe expectedResultConditionTruth
      }
    }
  }

  "a CompositeCondition" should {
    "never be evaluated by invoking isTrue" in {
      val compositeCondition = new CompositeCondition {}

      val mockAuthContext = mock[AuthContext]
      val mockRuleContext = mock[RuleContext]

      the[RuntimeException] thrownBy {
        compositeCondition.isTrue(mockAuthContext, mockRuleContext)
      } should have message "This should never be called"
    }
  }

  "the 'when' operator" should {

    val location = evaluateUsingPlay {
      Location.Type("url", "name")
    }

    val mockAuthContext = mock[AuthContext]
    val mockRuleContext = mock[RuleContext]
    val mockAuditContext = mock[TAuditContext]

    val scenarios = Table(
      ("scenario", "conditionTruth", "expectedLocation"),
      ("condition is true", true, Some(location)),
      ("condition is false", false, None)
    )

    forAll(scenarios) { (scenario: String, conditionTruth: Boolean, expectedLocation: Option[Location.LocationType]) =>

      s"return a rule given a condition - scenario: $scenario" in {
        val condition = new Condition {
          override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(conditionTruth)

          override val auditType: Option[RoutingReason] = None
        }

        val rule = Condition.when(condition).thenGoTo(location)

        val ruleResult: Option[LocationType] = await(rule.apply(mockAuthContext, mockRuleContext, mockAuditContext))

        ruleResult shouldBe expectedLocation
      }
    }
  }
}
