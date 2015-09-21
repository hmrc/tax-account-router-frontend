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

import config.WSHttp
import play.api.mvc._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.{Actions, AuthContext, GovernmentGateway}
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future


object RouterController extends RouterController {
  override protected def authConnector: AuthConnector = FrontendAuthConnector
}

object FrontendAuthConnector extends AuthConnector with ServicesConfig {
  val serviceUrl = baseUrl("auth")
  lazy val http = WSHttp
}

trait RouterController extends FrontendController with Actions {

  def destinations: List[Destination] = List()

  val defaultDestination: Destination = Yta

  val account = AuthenticatedBy(CompanyAuthGovernmentGateway).async { user => request => route(user, request) }

  def route(user: AuthContext, request: Request[AnyContent]): Future[Result] = {
    val nextDestination = destinations.find(_.shouldGo(user, request)).getOrElse(defaultDestination)
    Future.successful(Redirect(nextDestination.location))
  }
}

trait Destination {
  def shouldGo(user: AuthContext, request: Request[AnyContent]): Boolean

  val location: String
}

object IV extends Destination {
  override def shouldGo(user: AuthContext, request: Request[AnyContent]): Boolean = false

  // is the iv done?
  override val location: String = "/account/iv"
}

object SaPrefs extends Destination {
  override def shouldGo(user: AuthContext, request: Request[AnyContent]): Boolean = ???

  // is the pref there?
  override val location: String = "/account/sa/print-preference"
}

object Pta extends Destination {
  override def shouldGo(user: AuthContext, request: Request[AnyContent]): Boolean = ???

  override val location: String = "/personal-tax"
}

object Yta extends Destination {
  override def shouldGo(user: AuthContext, request: Request[AnyContent]): Boolean = true

  override val location: String = ExternalUrls.businessTaxAccountUrl
}

object CompanyAuthGovernmentGateway extends GovernmentGateway {
  lazy val login: String = ExternalUrls.signIn
}