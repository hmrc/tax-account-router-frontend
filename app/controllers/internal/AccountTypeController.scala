/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.{Logger, LoggerLike, Play}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.EnumJson._
import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits.global
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
  val extendedLoggingEnabled = Play.configuration.getBoolean("extended-logging-enabled").getOrElse(false)

  def defaultAccountType: AccountType.AccountType

  def logger: LoggerLike

  def ruleEngine: RuleEngine

  def createRuleContext(credId: String)(implicit request: Request[AnyContent], hc: HeaderCarrier): RuleContext

  def accountTypeForCredId(credId: String) = Action.async { implicit request =>
    val ruleContext = createRuleContext(credId)

    ruleContext.affinityGroup.flatMap {
      case AffinityGroupValue.AGENT =>
        Future.successful(Ok(Json.toJson(AccountTypeResponse(AccountType.Agent))))

      case affinityValue =>
        val engineResult: EngineResult = ruleEngine.getLocation(ruleContext)

        val finalResult: Future[AccountTypeResponse] = engineResult.value map { location =>
          val accountType = accountTypeBasedOnLocation(location)
          AccountTypeResponse(accountType)
        }

        if (extendedLoggingEnabled) {
          engineResult.run map {
            case (auditInfo, _) => Logger.warn(s"[AIV-1264 (internal)] ${auditInfo.ruleApplied.getOrElse("No rule applied.")}")
          }
        }

        //[AIV-1349]
        val fprResult = fourpartruleEvaluate(affinityValue, engineResult)

        for {
          tarResponse <- finalResult
          fourprResponse <- fprResult
        } yield{
          compareAndLog(tarResponse, fourprResponse)
          Ok(Json.toJson(tarResponse))
        }

    }.recover {
      case e =>
        logger.error("Unable to get user details from downstream.", e)
        InternalServerError("Unable to get user details from downstream.")
    }
  }

  //[AIV-1349]
  private def compareAndLog(tar: AccountTypeResponse, fprResult: AccountTypeResponse) = {
    if (tar.`type`.equals(fprResult.`type`)) Logger.warn(s"[AIV-1349] TAR and 4PR agree that login is ${tar.`type`}.")
    else Logger.warn(s"[AIV-1349] TAT and 4PR disagree, TAR identifies login as ${tar.`type`}, but 4PR identifies login as ${fprResult.`type`}")
  }

  //[AIV-1349]
  private def fourpartruleEvaluate(affinityValue: String, engineResult: EngineResult): Future[AccountTypeResponse] = {
    affinityValue match {
      case "Individual" => Future.successful(AccountTypeResponse(AccountType.Individual))
      case "Organisation" =>
        engineResult.value map { location =>
          val accountType = accountTypeBasedOnLocation(location)
          AccountTypeResponse(accountType)
        }
      case "Agent" => Future.successful(AccountTypeResponse(AccountType.Agent))
      case _ => Future.successful(AccountTypeResponse(AccountType.Organisation))
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
