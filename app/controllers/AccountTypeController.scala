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

package controllers

import connector.FrontendAuthConnector
import controllers.AccountType.AccountType
import engine.RuleEngine
import model.Locations._
import model._
import play.api.Logger
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.{Action, AnyContent, Request}
import services.{ThrottlingService, TwoStepVerification}
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.EnumJson._

object AccountType extends Enumeration {
  type AccountType = Value
  val Individual, Organisation = Value
}

case class AccountTypeResponse(`type`: AccountType)

object AccountTypeResponse {
  implicit val userTypeReads = enumFormat(AccountType)
  implicit val writes: Writes[AccountTypeResponse] = Json.writes[AccountTypeResponse]
  implicit val reads: Reads[AccountTypeResponse] = Json.reads[AccountTypeResponse]
}

object AccountTypeController extends AccountTypeController {
  override protected def authConnector = FrontendAuthConnector

  override val defaultLocation = BusinessTaxAccount

  override val ruleEngine = TarRules

  override val throttlingService = ThrottlingService

  override val twoStepVerification = TwoStepVerification

  override def createAuditContext() = AuditContext()
}

trait AccountTypeController extends FrontendController with Actions {
  def defaultLocation: Location

  def ruleEngine: RuleEngine

  def throttlingService: ThrottlingService

  def twoStepVerification: TwoStepVerification

  /**
    *
    * For the moment no audit event will be sent, but evaluation of the rules require the audit context
    * so it is not removed until it is confirmed. We will most probably need auditing here too, but with a different type.
    *
    */
  def createAuditContext(): TAuditContext

  def userTypeForCredId(credId: String) = Action.async { implicit request =>
    val ruleContext = RuleContext(Some(credId))
    val auditContext = createAuditContext()
    // TODO calculate final destination should be refactor to return business type on a deeper layer (as opposed to process the destination)
    calculateFinalDestination(ruleContext, auditContext) map {
      case Locations.PersonalTaxAccount => Ok(Json.toJson(AccountTypeResponse(AccountType.Individual)))
      case Locations.BusinessTaxAccount => Ok(Json.toJson(AccountTypeResponse(AccountType.Organisation)))
    }
  }

  private def calculateFinalDestination(ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent]) = {
    val ruleEngineResult = ruleEngine.getLocation(ruleContext, auditContext).map(nextLocation => nextLocation.getOrElse(defaultLocation))

    for {
      destinationAfterRulesApplied <- ruleEngineResult
      destinationAfterThrottleApplied <- throttlingService.throttle(destinationAfterRulesApplied, auditContext, ruleContext)
      finalDestination <- twoStepVerification.getDestinationVia2SV(destinationAfterThrottleApplied, ruleContext, auditContext).map(_.getOrElse(destinationAfterThrottleApplied))
    } yield {
      Logger.debug(s"routing to: ${finalDestination.name}")
      finalDestination
    }
  }
}
