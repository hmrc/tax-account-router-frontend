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

package auth

import controllers.ExternalUrls
import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.frontend.auth.{AuthContext, AuthenticationProvider, UserCredentials}

import scala.concurrent.Future

trait RouterAuthenticationProvider extends AuthenticationProvider {

  import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

  override val id = "RAP"

  def login: String = ExternalUrls.signIn

  override def redirectToLogin(redirectToOrigin: Boolean)(implicit request: Request[AnyContent]) = Future.successful(Redirect(login))

  override def handleNotAuthenticated(redirectToOrigin: Boolean)(implicit request: Request[AnyContent]): PartialFunction[UserCredentials, Future[Either[AuthContext, FailureResult]]] = {
    case UserCredentials(None, token@_) =>
      Logger.info(s"No userId found - redirecting to login. user: None token : $token")
      redirectToLogin(redirectToOrigin).map(Right(_))
  }
}

object RouterAuthenticationProvider extends RouterAuthenticationProvider