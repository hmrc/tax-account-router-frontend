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

import play.api.mvc._
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future


object RouterController extends RouterController

trait RouterController extends FrontendController {

  def destinations: List[Destination] = List(Login, IV, SaPrefs, Pta)

  val defaultDestination: Destination = Yta

  val account = Action.async { implicit request =>

    val nextDestination = destinations.find(_.shouldGo).getOrElse(defaultDestination)
    Future.successful(Redirect(nextDestination.location))

  }
}

trait Destination {
  def shouldGo(implicit request: Request[AnyContent]): Boolean
  val location: String
}

object Login extends Destination {
  override def shouldGo(implicit request: Request[AnyContent]): Boolean = ??? // is the cookie there?
  override val location: String = "/account/sign-in?continue=/account"
}

object IV extends Destination {
  override def shouldGo(implicit request: Request[AnyContent]): Boolean = false // is the iv done?
  override val location: String = "/account/iv"
}

object SaPrefs extends Destination {
  override def shouldGo(implicit request: Request[AnyContent]): Boolean = ??? // is the pref there?
  override val location: String = "/account/sa/print-preference"
}

object Pta extends Destination {
  override def shouldGo(implicit request: Request[AnyContent]): Boolean = ???
  override val location: String = "/personal-tax"
}

object Yta extends Destination {
  override def shouldGo(implicit request: Request[AnyContent]): Boolean = true
  override val location: String = "/business-tax-account"
}