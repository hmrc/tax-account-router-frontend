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

import auth.RouterAuthenticationProvider
import connector.FrontendAuthConnector
import services.WelcomePageService
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController

trait WelcomeController extends FrontendController with Actions {
  val welcomeService: WelcomePageService

  def welcome = AuthenticatedBy(RouterAuthenticationProvider) {
    implicit user => implicit request => {
      welcomeService.markWelcomePageAsSeen()
      Ok(views.html.welcome())
    }
  }
}

object WelcomeController extends WelcomeController {
  implicit override val authConnector = FrontendAuthConnector
  implicit override val welcomeService = WelcomePageService
}
