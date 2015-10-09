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

package services

import model.Location._
import model.{AuditContext, Location, Rule, RuleContext}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RuleServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  "rule service" should {

    "evaluate rules in order skipping those that should not be evaluated - should return /second/location" in {

      //given
      val firstRule = mock[Rule]
      when(firstRule.apply(any[AuthContext], any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future.successful(None)
      val secondRule = mock[Rule]
      val expectedLocation: LocationType = BTA
      when(secondRule.apply(any[AuthContext], any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Future(Some(expectedLocation)))
      val rules: List[Rule] = List(firstRule, secondRule)

      //and
      implicit lazy val request = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

      //when
      val maybeLocation: Future[Option[LocationType]] = RuleService.fireRules(rules, mock[AuthContext], mock[RuleContext], mock[AuditContext])(request, hc)

      //then
      val location: Option[LocationType] = await(maybeLocation)
      location shouldBe Some(expectedLocation)
    }

    "evaluate rules in order skipping those that should not be evaluated - should return /first/location" in {

      //given
      val firstRule = mock[Rule]
      val expectedLocation: LocationType = BTA
      when(firstRule.apply(any[AuthContext], any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(Some(expectedLocation))
      val secondRule = mock[Rule]
      when(secondRule.apply(any[AuthContext], any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(None)
      val rules: List[Rule] = List(firstRule, secondRule)

      //and
      implicit lazy val request = FakeRequest()
      implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(request.headers)

      //when
      val maybeLocation: Future[Option[LocationType]] = RuleService.fireRules(rules, mock[AuthContext], mock[RuleContext], mock[AuditContext])(request, hc)

      //then
      val location: Option[LocationType] = await(maybeLocation)
      location shouldBe Some(expectedLocation)

      //then
      verify(firstRule).apply(any[AuthContext], any[RuleContext], any[AuditContext])(eqTo(request), eqTo(hc))
      verify(secondRule, never()).apply(any[AuthContext], any[RuleContext], any[AuditContext])(any[Request[AnyContent]], any[HeaderCarrier])
    }

  }

}
