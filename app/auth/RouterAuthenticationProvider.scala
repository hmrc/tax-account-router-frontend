/*
 * Copyright 2020 HM Revenue & Customs
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

package auth

import controllers.ExternalUrls
import play.api.Logger
import play.api.mvc.Request
import play.api.mvc.Results._
import uk.gov.hmrc.play.frontend.auth.{AuthContext, AuthenticationProvider, UserCredentials}

import scala.concurrent.Future

trait RouterAuthenticationProvider extends AuthenticationProvider {

  override val id = "RAP"

  def login: String = ExternalUrls.signIn

  import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

  override def redirectToLogin(implicit request: Request[_]): Future[FailureResult] = Future.successful(Redirect(login))

  override def handleNotAuthenticated(implicit request: Request[_]): PartialFunction[UserCredentials, Future[Either[AuthContext, RouterAuthenticationProvider.FailureResult]]] = {
    case UserCredentials(None, token@_) =>
      Logger.info(s"No userId found - redirecting to login. user: None token : $token")
      redirectToLogin.map(Right(_))
  }
}

object RouterAuthenticationProvider extends RouterAuthenticationProvider
