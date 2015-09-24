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

package model

import controllers.routes
import play.api.mvc.{AnyContent, Request}
import services.WelcomePageService
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Destination {
  final def getLocation(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Option[String]] = shouldGo.map {
    case true => Some(url)
    case _ => None
  }

  protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean]

  protected val url: String
}

object Welcome extends Destination {
  override protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = WelcomePageService.shouldShowWelcomePage

  override protected val url: String = routes.WelcomeController.welcome().url
}

/**
 * Other destinations that are likely to be added here:
 * - YTA (ExternalUrls.businessTaxAccountUrl)
 * - IV ("/account/iv")
 * - SaPrefs ("/account/sa/print-preference")
 */