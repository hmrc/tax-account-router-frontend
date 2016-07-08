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
import controllers.ExternalUrls
import engine.Condition._
import model.Locations._
import model._
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

trait TwoStepVerification {

  def twoStepVerificationHost: String

  def twoStepVerificationPath: String

  def twoStepVerificationEnabled: Boolean

  def twoStepVerificationThrottle: TwoStepVerificationThrottle

  private val conditionsByDestination = Map(
    BusinessTaxAccount -> List(not(HasStrongCredentials), GGEnrolmentsAvailable, HasOnlyOneEnrolment, HasSelfAssessmentEnrolments, not(HasRegisteredFor2SV))
  )

  private val locationToAppName = Map(
    BusinessTaxAccount -> "business-tax-account"
  )

  val continueToAccountUrl = s"${ExternalUrls.taxAccountRouterHost}/account"

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
          case true =>
            twoStepVerificationThrottle.registrationMandatory(authContext.user.oid) match {
              case true =>
                auditContext.setSentToMandatory2SVRegister()
                Some(wrapLocationWith2SV(continue, Locations.TaxAccountRouterHome))
              case _ =>
                auditContext.setSentToOptional2SVRegister()
                Some(wrapLocationWith2SV(continue, continue))
            }
          case _ => None
        }
      }
    } else Future.successful(None)
  }

  private def wrapLocationWith2SV(continue: Location, failure: Location) = Locations.twoStepVerification(Map("continue" -> continue.fullUrl, "failure" -> failure.fullUrl) ++
    locationToAppName.get(continue).fold(Map.empty[String, String])(origin => Map("origin" -> origin)))
}

object TwoStepVerification extends TwoStepVerification with AppConfigHelpers {

  override lazy val twoStepVerificationHost = getConfigurationString("two-step-verification.host")

  override lazy val twoStepVerificationPath = getConfigurationString("two-step-verification.path")

  override lazy val twoStepVerificationEnabled = getConfigurationBoolean("two-step-verification.enabled")

  override lazy val twoStepVerificationThrottle = TwoStepVerificationThrottle
}
