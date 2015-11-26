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

import config.WSHttp
import play.api.Logger
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.http.cache.client.{ShortLivedCache, ShortLivedHttpCaching}
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}
import uk.gov.hmrc.play.frontend.auth.{AuthContext, LoggedInUser}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

object WelcomePageService extends WelcomePageService {
  override val welcomePageSeenKey = "welcomePageSeen"

  override def shortLivedCache = ShortLivedCache
}

trait WelcomePageService {

  def welcomePageSeenKey: String

  def shortLivedCache: ShortLivedCache

  private def userCacheId(user: LoggedInUser) = user.userId.replace("/auth/oid/", "")

  def hasNeverSeenWelcomePageBefore(authContext: AuthContext)(implicit hc: HeaderCarrier): Future[Boolean] = {
    shortLivedCache.fetchAndGetEntry[Boolean](cacheId = userCacheId(authContext.user), key = welcomePageSeenKey).map {
      case Some(true) => false
      case _ => true
    }.recover {
      case t: Throwable => {
        Logger.warn(s"Error retrieving $welcomePageSeenKey", t)
        false
      }
    }
  }

  def markWelcomePageAsSeen()(implicit authContext: AuthContext, hc: HeaderCarrier): Future[Boolean] = {
    shortLivedCache.cache(cacheId = userCacheId(authContext.user), formId = welcomePageSeenKey, body = true).map {
      _ => true
    }.recover {
      case t: Throwable => {
        Logger.warn(s"Failed to update $welcomePageSeenKey flag in KeyStore", t)
        false
      }
    }
  }

}

object ShortLivedHttpCaching extends ShortLivedHttpCaching with AppName with ServicesConfig {
  override lazy val http = WSHttp
  override lazy val defaultSource = "business-tax-account"
  override lazy val baseUri = baseUrl("cachable.short-lived-cache")
  override lazy val domain = getConfString("cachable.short-lived-cache.domain", throw new Exception(s"Could not find config 'cachable.short-lived-cache.domain'"))
}

object ShortLivedCache extends ShortLivedCache {
  override implicit lazy val crypto = ApplicationCrypto.JsonCrypto
  override lazy val shortLiveCache = ShortLivedHttpCaching
}
