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

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class RouterControllerSpec extends UnitSpec with WithFakeApplication {

  "router controller" should {

    "evaluate destinations in order skipping locations that should not be visited" in {

      val scenarios =
        Table(
          ("scenario", "destinationList", "expectedLocation"),
          ("should visit /second/location", List(new DestinationStub(false, "/first/location"), new DestinationStub(true, "/second/location")), "/second/location"),
          ("should visit /fist/location", List(new DestinationStub(true, "/first/location"), new DestinationStub(true, "/second/location")), "/first/location")
        )

      forAll(scenarios) { (scenario: String, destinationList: List[DestinationStub], expectedLocation: String) =>
        val controller = new RouterController {
          override def destinations: List[Destination] = destinationList
        }
        val futureResult: Future[Result] = controller.account.apply(FakeRequest())
        val result = await(futureResult)
        result.header.status shouldBe 303
        result.header.headers("Location") shouldBe expectedLocation
      }
    }

    "evaluate destinations in order going to the default location when all the other locations should not be visited" in {
      val controller = new RouterController {
        override def destinations: List[Destination] = List(
          new DestinationStub(false, "/first/location"),
          new DestinationStub(false, "/second/location")
        )

        override val defaultDestination: Destination = new DestinationStub(true, "/third/location")
      }
      val futureResult: Future[Result] = controller.account.apply(FakeRequest())
      val result = await(futureResult)
      result.header.status shouldBe 303
      result.header.headers("Location") shouldBe "/third/location"
    }
  }
}

class DestinationStub(expectedShouldGo: Boolean, expectedLocation: String) extends Destination {
  override def shouldGo(implicit request: Request[AnyContent]): Boolean = expectedShouldGo

  override val location: String = expectedLocation
}