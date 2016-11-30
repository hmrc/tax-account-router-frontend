/*
 * Copyright 2016 HM Revenue & Customs
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

import connector.FrontendAuthConnector
import controllers.TarRules
import controllers.internal.AccountType.AccountType
import engine.RuleEngine
import model._
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.Action
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.EnumJson._

object AccountType extends Enumeration {
  type AccountType = Value
  val Individual, Organisation = Value
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

  override def createAuditContext() = AuditContext()

}

trait AccountTypeController extends FrontendController with Actions {
  def defaultAccountType: AccountType.AccountType

  def logger: LoggerLike

  def ruleEngine: RuleEngine

  /**
    *
    * For the moment no audit event will be sent, but evaluation of the rules require the audit context
    * so it is not removed until it is confirmed. We will most probably need auditing here too, but with a different type.
    *
    */
  def createAuditContext(): TAuditContext

  def accountTypeForCredId(credId: String) = Action.async { implicit request =>
    val ruleContext = RuleContext(Some(credId))
    val auditContext = createAuditContext()
    // TODO calculate final destination should be refactor to return business type on a deeper layer (as opposed to process the destination)
    ruleEngine.matchRulesForLocation(ruleContext, auditContext) map { location =>
      val accountType = accountTypeBasedOnLocation(location)
      Ok(Json.toJson(AccountTypeResponse(accountType)))
    }
  }

  private def accountTypeBasedOnLocation(location: Location) = location match {
    case Locations.PersonalTaxAccount => AccountType.Individual
    case Locations.BusinessTaxAccount => AccountType.Organisation
    case unknownLocation: Location =>
      logger.warn(s"Location is ${unknownLocation.fullUrl} is not recognised as PTA or BTA. Returning default type.")
      defaultAccountType
  }

}
