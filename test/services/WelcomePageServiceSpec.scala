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

import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.libs.json.{Reads, Writes}
import play.api.test.FakeRequest
import uk.gov.hmrc.http.cache.client.{CacheMap, ShortLivedCache}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser, Principal}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WelcomePageServiceSpec extends UnitSpec with MockitoSugar {

  "WelcomePageService" should {
    //given
    val welcomePageSeenKey = "welcomePageSeenKey"

    //and
    val cacheId = "userId"
    val userId = s"/auth/oid/$cacheId"
    val user = mock[LoggedInUser]
    when(user.userId).thenReturn(userId)

    //and
    val authContext = AuthContext(user, Principal(None, Accounts()), None)
    implicit val fakeRequest = FakeRequest()
    implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

    //and
    val scenariosWelcomePageSeen = Table(
      ("scenario", "shortLivedCacheContent", "expectedResult"),
      ("Welcome page seen before", Some(true), true),
      ("Welcome page not seen before with false value", Some(false), false),
      ("Welcome page not seen before with none value", None, false)
    )

    forAll(scenariosWelcomePageSeen) { (scenario: String, shortLivedCacheContent: Option[Boolean], expectedResult: Boolean) =>

      s"return whether the welcome page has been seen - scenario:$scenario" in {
        //and
        val shortLivedCache = mock[ShortLivedCache]
        when(shortLivedCache.fetchAndGetEntry[Boolean](cacheId, welcomePageSeenKey)).thenReturn(Future(shortLivedCacheContent))

        //and
        val welcomePageService = new WelcomePageServiceTest(welcomePageSeenKey, shortLivedCache)

        //when
        val result = await(welcomePageService.hasWelcomePageBeenSeenBefore(authContext))

        //then
        result shouldBe expectedResult

        //and
        verify(shortLivedCache).fetchAndGetEntry[Boolean](eqTo(cacheId), eqTo(welcomePageSeenKey))(eqTo(hc), any[Reads[Boolean]])
      }

    }

    "return true when mark welcome page as seen" in {
      //given
      val shortLivedCache = mock[ShortLivedCache]
      when(shortLivedCache.cache(cacheId, welcomePageSeenKey, true)).thenReturn(Future(mock[CacheMap]))

      //and
      val welcomePageService = new WelcomePageServiceTest(welcomePageSeenKey, shortLivedCache)

      //when
      val result = await(welcomePageService.markWelcomePageAsSeen()(authContext, hc))

      //then
      result shouldBe true

      //and
      verify(shortLivedCache).cache(eqTo(cacheId), eqTo(welcomePageSeenKey), eqTo(true))(eqTo(hc), any[Writes[Boolean]])
    }

  }

}

class WelcomePageServiceTest(override val welcomePageSeenKey: String, override val shortLivedCache: ShortLivedCache) extends WelcomePageService
