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

import org.joda.time.{DateTime, DateTimeUtils, DateTimeZone}
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.play.test.UnitSpec

class RoutingCookieValuesSpec extends UnitSpec with BeforeAndAfterEach {

  "RoutingCookieValues" should {

    "should return tuple when apply" in {
      //given
      val firstToken = "token1"
      val secondToken = "token2"
      val value = s"$firstToken#$secondToken"

      //when
      val valueReturned: RoutingCookieValues = RoutingCookieValues(value)

      //then
      valueReturned shouldBe RoutingCookieValues(firstToken, secondToken)
    }

    "should return toString with right format" in {
      //given
      val routedDestination = "routedDestination"
      val throttledDestination = "throttledDestination"

      //when
      val toStringReturned: String = RoutingCookieValues(routedDestination = routedDestination, throttledDestination = throttledDestination).toString

      //then
      toStringReturned shouldBe s"$routedDestination#$throttledDestination"
    }

  }

  "CookieMaxAge" should {

    "Duration" in {
      //given
      val seconds = 1

      //when
      val maxAgeReturned = Duration(seconds)

      //then
      maxAgeReturned.getExpirationTime shouldBe DateTime.now(DateTimeZone.UTC).plusSeconds(seconds)
    }

    "Instant" in {
      //given
      val now = DateTime.now()

      //when
      val maxAgeReturned = Instant(now)

      //then
      maxAgeReturned.getExpirationTime shouldBe DateTime.now()
    }

  }

  override protected def beforeEach(): Unit = DateTimeUtils.setCurrentMillisFixed(0)

  override protected def afterEach(): Unit = DateTimeUtils.setCurrentMillisSystem()
}
