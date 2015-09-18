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

import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class RouterControllerSpec extends UnitSpec with WithFakeApplication {

  "router controller" should {
    "evaluate locations to go to in order skipping locations that should not be visited #1" in {
      val controller = new RouterController {
        override def locationsToGoTo: List[LocationToGoTo] = List(
          new LocationToGoToStub(false, "/first/location"),
          new LocationToGoToStub(true, "/second/location")
        )
      }
      val futureResult: Future[Result] = controller.account.apply(FakeRequest())
      val result = await(futureResult)
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe "/second/location"
    }

    "evaluate locations to go to in order skipping locations that should not be visited #2" in {
      val controller = new RouterController {
        override def locationsToGoTo: List[LocationToGoTo] = List(
          new LocationToGoToStub(true, "/first/location"),
          new LocationToGoToStub(true, "/second/location")
        )
      }
      val futureResult: Future[Result] = controller.account.apply(FakeRequest())
      val result = await(futureResult)
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe "/first/location"
    }

    "evaluate locations to go to in order going to the default location when all the other locations should not be visited" in {
      val controller = new RouterController {
        override def locationsToGoTo: List[LocationToGoTo] = List(
          new LocationToGoToStub(false, "/first/location"),
          new LocationToGoToStub(false, "/second/location")
        )

        override val defaultLocation: LocationToGoTo = new LocationToGoToStub(true, "/third/location")
      }
      val futureResult: Future[Result] = controller.account.apply(FakeRequest())
      val result = await(futureResult)
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe "/third/location"
    }
  }
}

class LocationToGoToStub(expectedShouldGo: Boolean, expectedLocation: String) extends LocationToGoTo {
  override def shouldGo(implicit request: Request[AnyContent]): Boolean = expectedShouldGo
  override val location: String = expectedLocation
}