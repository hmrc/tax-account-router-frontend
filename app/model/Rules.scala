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

package model

import connector._
import model.AuditEventType._
import model.Location._
import play.api.Play
import play.api.Play.current
import play.api.mvc.{AnyContent, Request}
import services.{RuleService, WelcomePageService}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

trait Rule {

  val subRules: List[Rule] = List()

  val defaultLocation: Option[LocationType]

  val ruleService: RuleService = RuleService

  def apply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Option[LocationType]] =

    shouldApply(authContext, ruleContext, auditContext).flatMap {
      case true =>
        val nextLocation: Future[Option[LocationType]] = ruleService.fireRules(subRules, authContext, ruleContext, auditContext)
        nextLocation.map {
          case Some(location) => Some(location)
          case None => defaultLocation match {
            case None => None
            case _ => defaultLocation
          }
        }
      case false => Future(None)
    }

  def shouldApply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean]
}

object GovernmentGatewayRule extends Rule {
  override val subRules: List[Rule] = List(HasAnyBusinessEnrolment, HasSelfAssessmentEnrolments)

  override val defaultLocation: Option[LocationType] = Some(BTA)

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(request.session.data.contains("token"))
}

object HasAnyBusinessEnrolment extends Rule {
  lazy val businessEnrolments: Set[String] = Play.configuration.getStringSeq("business-enrolments").getOrElse(Seq()).toSet[String]

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
    val hasBusinessEnrolments: Future[Boolean] = ruleContext.activeEnrolments.map(_.intersect(businessEnrolments).nonEmpty)

    auditContext.setValue(HAS_BUSINESS_ENROLMENTS, hasBusinessEnrolments)

    hasBusinessEnrolments
  }

  override val defaultLocation: Option[LocationType] = Some(BTA)
}

object HasSelfAssessmentEnrolments extends Rule {
  override val subRules: List[Rule] = List(WithNoPreviousReturns, IsInPartnershipOrSelfEmployed, IsNotInPartnershipNorSelfEmployed)
  lazy val selfAssessmentEnrolments: Set[String] = Play.configuration.getStringSeq("self-assessment-enrolments").getOrElse(Seq()).toSet[String]

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
    val hasSaEnrolments: Future[Boolean] = ruleContext.activeEnrolments.map(_.intersect(selfAssessmentEnrolments).nonEmpty)
    auditContext.setValue(HAS_SA_ENROLMENTS, hasSaEnrolments)
    hasSaEnrolments
  }

  override val defaultLocation: Option[LocationType] = None
}

object IsInPartnershipOrSelfEmployed extends Rule {
  override val defaultLocation: Option[LocationType] = Some(BTA)

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    ruleContext.lastSaReturn.map(saUserInfo => {
      val hasPreviousReturns: Boolean = saUserInfo.previousReturns
      val isInPartnership: Boolean = saUserInfo.partnership
      val isSelfEmployed: Boolean = saUserInfo.selfEmployment

      auditContext.setValue(HAS_PREVIOUS_RETURNS, Future(hasPreviousReturns))
      auditContext.setValue(IS_IN_A_PARTNERSHIP, Future(isInPartnership))
      auditContext.setValue(IS_SELF_EMPLOYED, Future(isSelfEmployed))

      hasPreviousReturns && (isInPartnership || isSelfEmployed)
    })
}

object IsNotInPartnershipNorSelfEmployed extends Rule {
  override val defaultLocation: Option[LocationType] = Some(PTA)

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    ruleContext.lastSaReturn.map(saUserInfo => {
      val hasPreviousReturns: Boolean = saUserInfo.previousReturns
      val isInPartnership: Boolean = saUserInfo.partnership
      val isSelfEmployed: Boolean = saUserInfo.selfEmployment

      auditContext.setValue(HAS_PREVIOUS_RETURNS, Future(hasPreviousReturns))
      auditContext.setValue(IS_IN_A_PARTNERSHIP, Future(isInPartnership))
      auditContext.setValue(IS_SELF_EMPLOYED, Future(isSelfEmployed))

      hasPreviousReturns && (!isInPartnership && !isSelfEmployed)
    })
}

object WithNoPreviousReturns extends Rule {
  override val defaultLocation: Option[LocationType] = Some(BTA)

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    ruleContext.lastSaReturn.map(saUserInfo => {
      val hasPreviousReturns: Boolean = saUserInfo.previousReturns

      auditContext.setValue(HAS_PREVIOUS_RETURNS, Future(hasPreviousReturns))

      !hasPreviousReturns
    })
}


trait WelcomePageRule extends Rule {
  val welcomePageService: WelcomePageService = WelcomePageService

  override val defaultLocation: Option[LocationType] = Some(WELCOME)

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
    val result: Future[Boolean] = welcomePageService.shouldShowWelcomePage(authContext, hc)
    auditContext.setValue(HAS_ALREADY_SEEN_WELCOME_PAGE, result.map(!_))
    result
  }
}

object WelcomePageRule extends WelcomePageRule

object VerifyRule extends Rule {
  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(!request.session.data.contains("token"))

  override val defaultLocation: Option[LocationType] = Some(PTA)
}

case class RuleContext(authContext: AuthContext)(implicit hc: HeaderCarrier) {
  val governmentGatewayConnector: GovernmentGatewayConnector = GovernmentGatewayConnector
  val selfAssessmentConnector: SelfAssessmentConnector = SelfAssessmentConnector

  lazy val activeEnrolments: Future[Set[String]] = {
    val futureProfile: Future[ProfileResponse] = governmentGatewayConnector.profile
    futureProfile.map { profile =>
      profile.enrolments.filter(_.state == EnrolmentState.ACTIVATED).map(_.key).toSet[String]
    }
  }

  lazy val lastSaReturn: Future[SaReturn] = authContext.principal.accounts.sa
    .fold(Future(SaReturn.empty))(saAccount => selfAssessmentConnector.lastReturn(saAccount.utr.value))
}