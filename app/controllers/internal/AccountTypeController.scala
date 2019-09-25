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

import connector._
import controllers.TarRules
import controllers.internal.AccountType.AccountType
import engine._
import model._
import play.api.Play.current
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.{Action, AnyContent, Request}
import play.api.{Logger, LoggerLike, Play}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.EnumJson._

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
  override def authConnector = FrontendAuthConnector

  override def userDetailsConnector = UserDetailsConnector

  override val defaultAccountType = AccountType.Organisation

  override val ruleEngine = TarRules

  override val logger = Logger

  override def createRuleContext(credId: String)(implicit request: Request[AnyContent], hc: HeaderCarrier) = RuleContext(Some(credId))
}

trait AccountTypeController extends FrontendController with Actions {
  val extendedLoggingEnabled = Play.configuration.getBoolean("extended-logging-enabled").getOrElse(false)

  def authConnector: FrontendAuthConnector

  def userDetailsConnector: UserDetailsConnector

  def defaultAccountType: AccountType.AccountType

  def logger: LoggerLike

  def ruleEngine: RuleEngine

  def createRuleContext(credId: String)(implicit request: Request[AnyContent], hc: HeaderCarrier): RuleContext

  def accountTypeForCredId(credId: String): Action[AnyContent] = Action.async { implicit request =>

    val ruleContext = createRuleContext(credId)

    ruleContext.affinityGroup.flatMap {
      case AffinityGroupValue.AGENT =>
        Future.successful(Ok(Json.toJson(AccountTypeResponse(AccountType.Agent))))
      case _ =>
        val engineResult: EngineResult = ruleEngine.getLocation(ruleContext)

        val finalResult = engineResult.value map { location  =>
          val accountType = accountTypeBasedOnLocation(location)
          AccountTypeResponse(accountType)
        }

        val ruleApplied: Future[String] = if (extendedLoggingEnabled) {
          engineResult.run map {
            case (auditInfo, _) => s"${auditInfo.ruleApplied.getOrElse("No rule applied")}"
          }
        } else Future.successful("")

        //[AIV-1349]
        val fourprResult = accountTypeForCredIdUsingFourPartRule(credId)

        for {
          tarResponse <- finalResult
          fourprResponse <- fourprResult
          tarRuleApplied <- ruleApplied
        } yield{
          compareAndLog(tarResponse, fourprResponse, tarRuleApplied)
          Ok(Json.toJson(tarResponse))
        }

    }.recover {
      case e =>
        logger.error("Unable to get user details from downstream.", e)
        InternalServerError("Unable to get user details from downstream.")
    }
  }

  //[AIV-1349]
  def accountTypeForCredIdUsingFourPartRule(credId: String)(implicit requestAnyContent: Request[AnyContent]): Future[AccountTypeResponse] = {

    val businessEnrolments: Set[String] = Conditions.config.businessEnrolments

    val userAuthority: Future[UserAuthority] = authConnector.userAuthority(credId)

    val userDetails: Future[UserDetails] = userAuthority.flatMap { authority =>
      authority.userDetailsUri.map(
        userDetailsConnector.getUserDetails
      ).getOrElse {
        Future.failed(new RuntimeException("userDetailsUri is not defined"))
      }
    }

    val userAffinityGroup = userDetails.map(_.affinityGroup)

    val userEnrolments: Future[Seq[GovernmentGatewayEnrolment]] = userAuthority.flatMap { authority =>
      val noEnrolments = Future.successful(Seq.empty[GovernmentGatewayEnrolment])
      authority.enrolmentsUri.fold(noEnrolments)(authConnector.getEnrolments)
    }

    val userActiveEnrolments: Future[Seq[GovernmentGatewayEnrolment]] = userEnrolments.map { enrolmentSeq =>
      enrolmentSeq.filter(_.state == EnrolmentState.ACTIVATED)
    }

    val userActiveEnrolmentKeys: Future[Set[String]] =
      userActiveEnrolments.map(
        activedEnrolmentsList => {
          activedEnrolmentsList.map(_.key).toSet[String]
        }
      )

    val userHasActiveBusinessEnrolments: Future[Boolean] = {
      for {
        activedEnrolmentKeys <- userActiveEnrolmentKeys
      } yield {
        logger.warn(s"[AIV-1349] the userActiveEnrolments are: $activedEnrolmentKeys")
        businessEnrolments.exists(activedEnrolmentKeys.contains)
      }
    }

    fourpartruleEvaluate(userAffinityGroup, userHasActiveBusinessEnrolments).recover{case t: Throwable =>
      logger.warn(s"[AIV-1349] the fourpartruleEvaluate fails set account type to Organisation as default: ${t.getMessage}")
      AccountTypeResponse(AccountType.Organisation)
    }
  }

  //[AIV-1349]
  def compareAndLog(tar: AccountTypeResponse, fprResult: AccountTypeResponse, ruleApplied: String): Unit = {
    if (extendedLoggingEnabled) {
      if (tar.`type`.equals(fprResult.`type`)) logger.warn(s"[AIV-1349] TAR and 4PR agree that login is ${tar.`type`}, TAR applying the rule: $ruleApplied.")
      else logger.warn(s"[AIV-1349] TAR and 4PR disagree, TAR identifies login as ${tar.`type`} by applying rule $ruleApplied, but 4PR identifies login as ${fprResult.`type`}")
    }
  }

  //[AIV-1349]

  // The four part rule being tested is :-
  //   If the user has an agent affinity group it is an agent
  //   else if the user has active business enrolments it is a organization
  //   else if the user has an individual affinity group it is an individual
  //   else it is an organization

  def fourpartruleEvaluate(affinityValue: Future[String], userHasActiveBusinessEnrolments: Future[Boolean]): Future[AccountTypeResponse] = {
    for {
      affinity: String <- affinityValue
      hasActiveBusinessEnrolment: Boolean <- userHasActiveBusinessEnrolments
    } yield {
      (affinity.toLowerCase, hasActiveBusinessEnrolment) match {
        case ("agent", _)      => AccountTypeResponse(AccountType.Agent)
        case (_, true)         => AccountTypeResponse(AccountType.Organisation)
        case ("individual", _) => AccountTypeResponse(AccountType.Individual)
        case _                 => AccountTypeResponse(AccountType.Organisation)
      }
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
