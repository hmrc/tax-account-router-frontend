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

package controllers.internal

import cats.data.WriterT
import connector.AffinityGroupValue
import controllers.internal.AccountTypeResponse.accountTypeReads
import engine.{AuditInfo, RuleEngine}
import helpers.VerifyLogger
import model.{Location, Locations, RuleContext}
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import support.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.filters.MicroserviceFilterSupport

import scala.concurrent.Future

class AccountTypeControllerSpec extends UnitSpec with MockitoSugar with OneAppPerSuite with Eventually with MicroserviceFilterSupport {

  "Account type controller " should {

    "return type Organisation when BTA location is provided by rules and there is an origin for this location" in new Setup {
      // given
      val engineResult = WriterT(Future.successful((mockAuditInfo, Locations.BusinessTaxAccount)))
      when(mockRuleEngine.getLocation(mockRuleContext)).thenReturn(engineResult)
      when(mockRuleContext.affinityGroup).thenReturn(Future.successful(AffinityGroupValue.ORGANISATION))

      // when
      val result = await(controller.accountTypeForCredId(credId)(FakeRequest()))

      // then
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "type").as[AccountType.AccountType] shouldBe AccountType.Organisation

      verify(mockRuleEngine).getLocation(mockRuleContext)

      verifyNoMoreInteractions(allMocksExceptAuditInfo: _*)
    }

    "return type Individual when PTA location is provided by rules and there is an origin for this location" in new Setup {
      // given
      val engineResult = WriterT(Future.successful((mockAuditInfo, Locations.PersonalTaxAccount)))
      when(mockRuleEngine.getLocation(mockRuleContext)).thenReturn(engineResult)
      when(mockRuleContext.affinityGroup).thenReturn(Future.successful(AffinityGroupValue.INDIVIDUAL))

      // when
      val result = await(controller.accountTypeForCredId(credId)(FakeRequest()))

      // then
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "type").as[AccountType.AccountType] shouldBe AccountType.Individual

      verify(mockRuleEngine).getLocation(mockRuleContext)

      verifyNoMoreInteractions(allMocksExceptAuditInfo: _*)
    }

    "return default account type when an unknown location is provided by rules (not PTA or BTA)" in new Setup {
      // given
      val unknownLocation = Location("unknown-location", "/unknown-location")
      val engineResult = WriterT(Future.successful((mockAuditInfo, unknownLocation)))
      when(mockRuleEngine.getLocation(mockRuleContext)).thenReturn(engineResult)
      when(mockRuleContext.affinityGroup).thenReturn(Future.successful(AffinityGroupValue.ORGANISATION))

      // when
      val result = await(controller.accountTypeForCredId(credId)(FakeRequest()))

      // then
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "type").as[AccountType.AccountType] shouldBe theDefaultAccountType

      verify(mockRuleEngine).getLocation(mockRuleContext)

      verifyWarningLogging(s"Location ${unknownLocation.url} is not recognised as PTA or BTA. Returning default type.", 2)

      verifyNoMoreInteractions(allMocksExceptAuditInfo: _*)
    }

    "return agent account type when the affinity group is agent" in new Setup {
      // given
      when(mockRuleContext.affinityGroup).thenReturn(Future.successful(AffinityGroupValue.AGENT))

      // when
      val result = await(controller.accountTypeForCredId(credId)(FakeRequest()))

      // then
      status(result) shouldBe 200

      import AccountTypeResponse.accountTypeReads

      (jsonBodyOf(result) \ "type").as[AccountType.AccountType] shouldBe AccountType.Agent

      verifyNoMoreInteractions(allMocksExceptAuditInfo: _*)
    }

  }

  trait Setup extends VerifyLogger {
    val mockAuditInfo = mock[AuditInfo]
    val mockAuthConnector = mock[AuthConnector]
    val mockRuleContext = mock[RuleContext]
    val mockRuleEngine = mock[RuleEngine]

    val allMocksExceptAuditInfo = Seq(mockRuleEngine, mockAuthConnector, mockLogger)

    val credId = "credId"

    val theDefaultAccountType = AccountType.Organisation

    val ruleContextCaptor = ArgumentCaptor.forClass(classOf[RuleContext])

    val controller = new AccountTypeController {

      override val ruleEngine = mockRuleEngine

      override val defaultAccountType = theDefaultAccountType

      override val logger = mockLogger

      override def createRuleContext(credId: String)(implicit request: Request[AnyContent], hc: HeaderCarrier): RuleContext = mockRuleContext

      override protected def authConnector: AuthConnector = mockAuthConnector
    }
  }

}
