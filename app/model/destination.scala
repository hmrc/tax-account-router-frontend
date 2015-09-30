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

import controllers.{ExternalUrls, routes}
import play.api.mvc.{AnyContent, Request}
import services.WelcomePageService
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Location(url: String, name: String)

trait Destination {
  final def getLocation(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Option[Location]] = shouldGo.map {
    case true => Some(location)
    case _ => None
  }

  protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean]

  val location: Location
}

object Welcome extends Destination {
  override protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = WelcomePageService.shouldShowWelcomePage

  override val location: Location = Location(routes.WelcomeController.welcome().url, "welcome")
}

object BTA extends Destination {

  val businessEnrolments: Set[String] = Set("vrn", "ctUtr") //Refer to uk.gov.hmrc.play.frontend.auth.connectors.domain.Authority.Accounts in play-authorised-frontend

  override protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
    Future(user.principal.accounts.toMap.keySet.intersect(businessEnrolments).nonEmpty)
  }

  override val location: Location = Location(ExternalUrls.businessTaxAccountUrl, "business-tax-account")
}

object PTA extends Destination {
  override protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
    val data: Map[String, String] = request.session.data
    val token: Option[String] = data.get("token")
    token match {
      case Some(_) => Future(false)
      case None => Future(true)
    }
  }

  override val location: Location = Location(ExternalUrls.personalTaxAccountUrl, "personal-tax-account")
}

/**
 * Other destinations that are likely to be added here:
 * - YTA (ExternalUrls.businessTaxAccountUrl)
 * - IV ("/account/iv")
 * - SaPrefs ("/account/sa/print-preference")
 */