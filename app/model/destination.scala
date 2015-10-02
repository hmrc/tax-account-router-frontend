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

import connector.{EnrolmentState, GovernmentGatewayConnector, ProfileResponse}
import controllers.{ExternalUrls, routes}
import play.api.Play
import play.api.Play.current
import play.api.mvc.{AnyContent, Request}
import services.WelcomePageService
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

case class Location(url: String, name: String)

trait Destination {
  final def getLocation(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Option[Location]] = shouldGo.map {
    case true => Some(location)
    case _ => None
  }

  protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean]

  val location: Location
}

object Welcome extends Destination {
  override protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = WelcomePageService.shouldShowWelcomePage

  override val location: Location = Location(routes.WelcomeController.welcome().url, "welcome")
}

trait BTADestination extends Destination {


  val selfEmployedEnrolments: Set[String] = Play.configuration.getStringSeq("self-employed-enrolments").getOrElse(Seq()).toSet[String]

  override val location: Location = Location(ExternalUrls.businessTaxAccountUrl, "business-tax-account")

  val rules: List[BaseEnrolmentRule] = List(WithBusinessEnrolmentsRule, WithSAAndInPartnershipEnrolmentRule, WithSAAnSelfEmployeeEnrolmentRule)

  override protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
    if (!request.session.data.contains("token")) return Future(false)

    val userId = user.user.userId
    val futureProfile: Future[ProfileResponse] = governmentGatewayConnector.profile(userId)
    futureProfile.map { profile =>
      val activeEnrolmentKeys: Set[String] = profile.enrolments.filter(_.state == EnrolmentState.ACTIVATED).map(_.key).toSet[String]

      rules.exists(rule => rule.apply(activeEnrolmentKeys))
    }
  }

  val governmentGatewayConnector: GovernmentGatewayConnector
}

object BTA extends BTADestination {
  override val governmentGatewayConnector: GovernmentGatewayConnector = GovernmentGatewayConnector
}

object PTA extends Destination {
  override protected def shouldGo(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
    Future(!request.session.data.contains("token"))
  }

  override val location: Location = Location(ExternalUrls.personalTaxAccountUrl, "personal-tax-account")
}