/*
 * Copyright 2026 HM Revenue & Customs
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

import config.FrontendAppConfig
import play.api.i18n.I18nSupport
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.LoggingUtil

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountManagementController @Inject()(val authConnector: AuthConnector,
                                            appConfig: FrontendAppConfig,
                                            val messagesControllerComponents: MessagesControllerComponents)
                                           (implicit val ec: ExecutionContext)
  extends FrontendController(messagesControllerComponents) with I18nSupport with AuthorisedFunctions with LoggingUtil {

  def redirectToProfileManagement(redirect_uri: Option[String]): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised().retrieve(Retrievals.profile and Retrievals.affinityGroup) {
        case profile ~ affinityGroup => Future.successful(Results.Redirect(
          profile.getOrElse(throw new IllegalArgumentException(f"Invalid profile: $profile with affinity group: $affinityGroup")),
          Map("redirect_uri" -> Seq(redirectUrl(redirect_uri, affinityGroup))),
          FOUND))
      } recover {
        case _: NoActiveSession => toGGLogin(request.uri)
        case _: IllegalArgumentException => toGGLogin(request.uri)
        case e =>
          errorLog(s"[RedirectController][redirectToProfileManagement] Exception performing redirectToProfileManagement: ${e.getMessage}")
          throw e
      }
    }
  }

  def redirectToGroupProfileManagement(redirect_uri: Option[String]): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised().retrieve(Retrievals.groupProfile and Retrievals.affinityGroup) {
        case groupProfile ~ affinityGroup => Future.successful(Results.Redirect(
          groupProfile.getOrElse(throw new IllegalArgumentException(f"Invalid groupProfile: $groupProfile with affinity group: $affinityGroup")),
          Map("redirect_uri" -> Seq(redirectUrl(redirect_uri, affinityGroup))),
          FOUND))
      } recover {
        case _: NoActiveSession => toGGLogin(request.uri)
        case _: IllegalArgumentException => toGGLogin(request.uri)
        case e =>
          errorLog(s"[RedirectController][redirectToGroupProfileManagement] Exception performing redirectToGroupProfileManagement: ${e.getMessage}")
          throw e
      }
    }
  }

  private def redirectUrl(redirectUri: Option[String], affinityGroup: Option[AffinityGroup]): String =
    redirectUri.filter(uri => appConfig.redirectAllowlist.contains(uri))
      .getOrElse(affinityGroup.filter(_ == AffinityGroup.Agent).map(_ => appConfig.agentRedirectUrl).getOrElse(appConfig.redirectUrl))

  private def ggLoginUrl: String = s"${appConfig.basGatewayFrontend}/bas-gateway/sign-in"

  final lazy val defaultOrigin: String =
    appConfig.getConfigurationStringOption("sosOrigin")
      .orElse(appConfig.getConfigurationStringOption("appName"))
      .getOrElse("undefined")

  private def origin: String = defaultOrigin

  private def toGGLogin(continueUrl: String): Result =
    Redirect(
      ggLoginUrl,
      Map(
        "continue_url" -> Seq(continueUrl),
        "origin" -> Seq(origin)
      ))
}