/*
 * Copyright 2017 HM Revenue & Customs
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

package controllers.internal

import connector.{AffinityGroupValue, FrontendAuthConnector}
import controllers.TarRules
import controllers.internal.AccountType.AccountType
import engine._
import model._
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.{Action, AnyContent, Request}
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier
import utils.EnumJson._

import scala.concurrent.Future

object AccountType extends Enumeration {
  type AccountType = Value
  val Individual, Organisation, Agent = Value
}

case class AccountTypeResponse(`type`: AccountType)

object AccountTypeResponse {
  implicit val accountTypeReads = enumFormat(AccountType)
  implicit val writes: Writes[AccountTypeResponse] = Json.writes[AccountTypeResponse]
  implicit val reads: Reads[AccountTypeResponse] = Json.reads[AccountTypeResponse]
}

object AccountTypeController extends AccountTypeController {
  override protected def authConnector = FrontendAuthConnector

  override val defaultAccountType = AccountType.Organisation

  override val ruleEngine = TarRules

  override val logger = Logger

  override def createRuleContext(credId: String)(implicit request: Request[AnyContent], hc: HeaderCarrier) = RuleContext(Some(credId))
}

trait AccountTypeController extends FrontendController with Actions {
  def defaultAccountType: AccountType.AccountType

  def logger: LoggerLike

  def ruleEngine: RuleEngine

  def createRuleContext(credId: String)(implicit request: Request[AnyContent], hc: HeaderCarrier): RuleContext

  def accountTypeForCredId(credId: String) = Action.async { implicit request =>
    val ruleContext: RuleContext = createRuleContext(credId)

    ruleContext.affinityGroup.flatMap {
      case AffinityGroupValue.AGENT =>
        Future.successful(Ok(Json.toJson(AccountTypeResponse(AccountType.Agent))))
      case _ =>
        ruleEngine.getLocation(ruleContext).value map { location =>
          val accountType = accountTypeBasedOnLocation(location)
          Ok(Json.toJson(AccountTypeResponse(accountType)))
        }
    }.recover {
      case e =>
        logger.error("Unable to get user details from downstream.", e)
        InternalServerError("Unable to get user details from downstream.")
    }
  }

  private def accountTypeBasedOnLocation(location: Location) = location match {
    case Locations.PersonalTaxAccount => AccountType.Individual
    case Locations.BusinessTaxAccount => AccountType.Organisation
    case unknownLocation: Location =>
      logger.warn(s"Location ${unknownLocation.url} is not recognised as PTA or BTA. Returning default type.")
      defaultAccountType
  }
}
