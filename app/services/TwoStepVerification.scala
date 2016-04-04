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

package services

import config.AppConfigHelpers
import engine.Condition._
import model.Locations._
import model._
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future
import scala.util.Success

trait TwoStepVerification {

  def twoStepVerificationHost: String

  def twoStepVerificationPath: String

  def twoStepVerificationEnabled: Boolean

  private val conditionsByDestination = Map(
    BusinessTaxAccount -> List(not(HasStrongCredentials), GGEnrolmentsAvailable, HasOnlyOneEnrolment, HasSelfAssessmentEnrolments, not(HasRegisteredFor2SV))
  )

  def getDestinationVia2SV(continue: Location, ruleContext: RuleContext, auditContext: TAuditContext)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier) = {

    if (twoStepVerificationEnabled) {
      conditionsByDestination.get(continue).fold[Future[Option[Location]]](Future.successful(None)) { conditions =>
        val shouldRedirectTo2SV = conditions.foldLeft(Future.successful(true)) { (preconditionsTrue, condition) =>
          preconditionsTrue.flatMap { precondition =>
            if (!precondition) Future.successful(false)
            else condition.evaluate(authContext, ruleContext, auditContext)
          }
        }
        shouldRedirectTo2SV.map {
          case true => Some(wrapLocationWith2SV(continue))
          case _ => None
        }
      }.andThen { case Success(Some(_)) => auditContext.sentTo2SVRegister = true }
    } else Future.successful(None)
  }

  private def wrapLocationWith2SV(continue: Location) = Locations.twoStepVerification("continue" -> continue.fullUrl, "failure" -> continue.fullUrl)
}

object TwoStepVerification extends TwoStepVerification with AppConfigHelpers {

  override lazy val twoStepVerificationHost = getConfigurationString("two-step-verification.host")

  override lazy val twoStepVerificationPath = getConfigurationString("two-step-verification.path")

  override lazy val twoStepVerificationEnabled = getConfigurationBoolean("two-step-verification.enabled")
}
