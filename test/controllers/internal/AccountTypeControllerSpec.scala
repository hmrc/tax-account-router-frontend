/*
 * Copyright 2016 HM Revenue & Customs
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

package controllers.internal

import engine.RuleEngine
import model.{Locations, RuleContext, TAuditContext}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import play.api.LoggerLike
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class AccountTypeControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication with Eventually {


  "Account type controller " should {
    "return type Organisation when BTA location is provided by rules and there is an origin for this location" in new Setup {
      // given
      when(mockRuleEngine.matchRulesForLocation(any[RuleContext], any[TAuditContext])(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Future.successful(Locations.BusinessTaxAccount))
      // when
      val result = await(controller.accountTypeForCredId(credId)(FakeRequest()))

      // then
      status(result) shouldBe 200

      import AccountTypeResponse.accountTypeReads

      (jsonBodyOf(result) \ "type").as[AccountType.AccountType] shouldBe AccountType.Organisation

      verify(mockRuleEngine).matchRulesForLocation(ruleContextCaptor.capture(), any[TAuditContext])(any[Request[AnyContent]], any[HeaderCarrier])
      ruleContextCaptor.getValue.credId shouldBe Some(credId)
      verifyNoMoreInteractions(allMocks: _*)
    }

    "return type Individual when PTA location is provided by rules and there is an origin for this location" in new Setup {

      // given
      when(mockRuleEngine.matchRulesForLocation(any[RuleContext], any[TAuditContext])(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Future.successful(Locations.PersonalTaxAccount))
      // when
      val result = await(controller.accountTypeForCredId(credId)(FakeRequest()))

      // then
      status(result) shouldBe 200

      import AccountTypeResponse.accountTypeReads

      (jsonBodyOf(result) \ "type").as[AccountType.AccountType] shouldBe AccountType.Individual

      verify(mockRuleEngine).matchRulesForLocation(ruleContextCaptor.capture(), any[TAuditContext])(any[Request[AnyContent]], any[HeaderCarrier])
      ruleContextCaptor.getValue.credId shouldBe Some(credId)
      verifyNoMoreInteractions(allMocks: _*)
    }
  }

  trait Setup {
    val mockRuleEngine = mock[RuleEngine]
    val mockAuthConnector = mock[AuthConnector]
    val mockAuditContext = mock[TAuditContext]
    val mockLogger = mock[LoggerLike]

    val allMocks = Seq(mockRuleEngine, mockAuthConnector, mockAuditContext, mockLogger)

    val credId = "credId"

    val theDefaultAccountType = AccountType.Organisation

    val ruleContextCaptor = ArgumentCaptor.forClass(classOf[RuleContext])

    val controller = new AccountTypeController {

      override val ruleEngine = mockRuleEngine

      override val defaultAccountType = theDefaultAccountType

      override val logger = mockLogger

      /**
        *
        * For the moment no audit event will be sent, but evaluation of the rules require the audit context
        * so it is not removed until it is confirmed. We will most probably need auditing here too, but with a different type.
        *
        */
      override def createAuditContext(): TAuditContext = mockAuditContext

      override protected def authConnector: AuthConnector = mockAuthConnector
    }
  }

}
