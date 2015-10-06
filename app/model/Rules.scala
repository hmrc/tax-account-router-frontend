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

  val defaultLocation: Option[Location]

  val ruleService: RuleService = RuleService

  def apply(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Option[Location]] =

    shouldApply(authContext, ruleContext).flatMap {
      case true =>
        val nextLocation: Future[Option[Location]] = ruleService.fireRules(subRules, authContext, ruleContext)
        nextLocation.map {
          case Some(location) => Some(location)
          case None => defaultLocation match {
            case None => None
            case _ => defaultLocation
          }
        }
      case false => Future(None)
    }

  def shouldApply(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean]
}

object GovernmentGatewayRule extends Rule {
  override val subRules: List[Rule] = List(HasAnyBusinessEnrolment, HasSelfAssessmentEnrolments)

  override val defaultLocation: Option[Location] = Some(BTALocation)

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(request.session.data.contains("token"))
}

case object HasAnyBusinessEnrolment extends Rule {
  lazy val businessEnrolments: Set[String] = Play.configuration.getStringSeq("business-enrolments").getOrElse(Seq()).toSet[String]

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = ruleContext.activeEnrolments.map(_.intersect(businessEnrolments).nonEmpty)

  override val defaultLocation: Option[Location] = Some(BTALocation)
}

object HasSelfAssessmentEnrolments extends Rule {
  override val subRules: List[Rule] = List(WithNoPreviousReturns, IsInPartnershipOrSelfEmployed, IsNotInPartnershipNorSelfEmployed)
  lazy val selfAssessmentEnrolments: Set[String] = Play.configuration.getStringSeq("self-assessment-enrolments").getOrElse(Seq()).toSet[String]

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = ruleContext.activeEnrolments.map(_.intersect(selfAssessmentEnrolments).nonEmpty)

  override val defaultLocation: Option[Location] = None
}

object IsInPartnershipOrSelfEmployed extends Rule {
  override val defaultLocation: Option[Location] = Some(BTALocation)

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    ruleContext.saUserInfo.map(saUserInfo => saUserInfo.previousReturns && (saUserInfo.partnership || saUserInfo.selfEmployment))
}

object IsNotInPartnershipNorSelfEmployed extends Rule {
  override val defaultLocation: Option[Location] = Some(PTALocation)

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    ruleContext.saUserInfo.map(saUserInfo => saUserInfo.previousReturns && (!saUserInfo.partnership && !saUserInfo.selfEmployment))
}

object WithNoPreviousReturns extends Rule {
  override val defaultLocation: Option[Location] = Some(BTALocation)

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    ruleContext.saUserInfo.map(!_.previousReturns)
}



trait WelcomePageRule extends Rule {
  val welcomePageService: WelcomePageService = WelcomePageService

  override val defaultLocation: Option[Location] = Some(WelcomePageLocation)

  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = welcomePageService.shouldShowWelcomePage(authContext, hc)
}

object WelcomePageRule extends WelcomePageRule

object VerifyRule extends Rule {
  override def shouldApply(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = Future(!request.session.data.contains("token"))

  override val defaultLocation: Option[Location] = Some(PTALocation)
}

case class RuleContext(userId: String)(implicit hc: HeaderCarrier) {
  val governmentGatewayConnector : GovernmentGatewayConnector = GovernmentGatewayConnector
  val selfAssessmentGatewayConnector : SelfAssessmentGatewayConnector = SelfAssessmentGatewayConnector

  lazy val activeEnrolments: Future[Set[String]] = {
    val futureProfile: Future[ProfileResponse] = governmentGatewayConnector.profile(userId)
    futureProfile.map { profile =>
      profile.enrolments.filter(_.state == EnrolmentState.ACTIVATED).map(_.key).toSet[String]
    }
  }

  lazy val saUserInfo: Future[SAUserInfo] = {
    selfAssessmentGatewayConnector.getInfo(userId)
  }
}