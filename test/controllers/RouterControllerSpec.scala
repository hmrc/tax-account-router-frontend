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

package controllers

import model.{Destination, Welcome}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import services.WelcomePageService
import uk.gov.hmrc.http.cache.client.ShortLivedCache
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RouterControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  "router controller" should {

    "evaluate destinations in order skipping those that should not be visited - should visit /second/location" in {

      val firstDestination = mock[Destination]
      when(firstDestination.getLocation(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future.successful(None)
      val secondDestination = mock[Destination]
      when(secondDestination.getLocation(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(Some("/second/location"))

      val controller = new TestRouteController(_destinations = List(firstDestination, secondDestination))

      val futureResult: Future[Result] = controller.route(mock[AuthContext], FakeRequest())
      val result = await(futureResult)
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe "/second/location"

      verify(firstDestination).getLocation(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier])
      verify(secondDestination).getLocation(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier])
    }

    "evaluate destinations in order skipping those that should not be visited - should visit /first/location" in {

      val firstDestination = mock[Destination]
      when(firstDestination.getLocation(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(Some("/first/location"))
      val secondDestination = mock[Destination]
      when(secondDestination.getLocation(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier])) thenReturn Future(Some("/second/location"))

      val controller = new TestRouteController(_destinations = List(firstDestination, secondDestination))

      val futureResult: Future[Result] = controller.route(mock[AuthContext], FakeRequest())
      val result = await(futureResult)
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe "/first/location"

      verify(firstDestination).getLocation(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier])
      verify(secondDestination, never()).getLocation(any[AuthContext], any[Request[AnyContent]], any[HeaderCarrier])
    }

    "evaluate destinations in order going to the default location when all the other locations should not be visited" in {
      val controller = new TestRouteController(
        _destinations = List(new DestinationStub(false, "/first/location"), new DestinationStub(false, "/second/location")),
        _defaultLocation = "/third/location"
      )
      val futureResult: Future[Result] = controller.route(mock[AuthContext], FakeRequest())
      val result = await(futureResult)
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe "/third/location"
    }

    "have a list of destinations as expected" in {
      RouterController.destinations shouldBe List(Welcome)
    }
  }
}

class TestRouteController(_destinations: List[Destination], _defaultLocation: String = "/") extends RouterController {
  override val welcomePageService: WelcomePageService = WelcomePageServiceStub

  override def destinations: List[Destination] = _destinations

  override def defaultLocation: String = _defaultLocation

  override protected def authConnector: AuthConnector = ???
}

class DestinationStub(expectedShouldGo: Boolean, expectedLocation: String) extends Destination {
  override protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(expectedShouldGo)

  override protected val url: String = expectedLocation
}

object WelcomePageServiceStub extends WelcomePageService {
  override def welcomePageSeenKey: String = ???

  override def shortLivedCache: ShortLivedCache = ???

  override def shouldShowWelcomePage(implicit authContext: AuthContext, hc: HeaderCarrier): Future[Boolean] = Future(false)
}