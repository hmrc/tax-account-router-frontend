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

import connector.FrontendAuthConnector
import model.{Destination, Welcome}
import play.api.mvc._
import services.WelcomePageService
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.{Actions, AuthContext, GovernmentGateway}
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

object RouterController extends RouterController {
  override protected def authConnector: AuthConnector = FrontendAuthConnector

  override val welcomePageService: WelcomePageService = WelcomePageService

  override val defaultLocation = new Destination {
    override protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future.successful(true)

    override val url: String = ExternalUrls.businessTaxAccountUrl
    override val name: String = "business-tax-account"
  }

  override val destinations: List[Destination] = List(Welcome)
  override val controllerMetrics: ControllerMetrics = ControllerMetrics
}

trait RouterController extends FrontendController with Actions {

  val controllerMetrics: ControllerMetrics

  val welcomePageService: WelcomePageService

  def defaultLocation: Destination

  def destinations: List[Destination]

  val account = AuthenticatedBy(CompanyAuthGovernmentGateway).async { implicit user => request => route(user, request) }

  def route(implicit user: AuthContext, request: Request[AnyContent]): Future[Result] = {

    val nextDestination: Future[Option[Destination]] = destinations.foldLeft(Future[Option[Destination]](None)) {
      (location, destination) => location.flatMap(candidateLocation => if (candidateLocation.isDefined) location else destination.getLocation)
    }

    nextDestination.map(maybeDestination => {
      val destination: Destination = maybeDestination.getOrElse(defaultLocation)
      controllerMetrics.registerRedirectFor(destination.name)
      Redirect(destination.url)
    })
  }
}

object CompanyAuthGovernmentGateway extends GovernmentGateway {
  lazy val login: String = ExternalUrls.signIn
}