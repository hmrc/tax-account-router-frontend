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

package model

import connector._
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

case class RuleContext(authContext: AuthContext)(implicit hc: HeaderCarrier) {
  val selfAssessmentConnector: SelfAssessmentConnector = SelfAssessmentConnector
  val frontendAuthConnector: FrontendAuthConnector = FrontendAuthConnector
  val userDetailsConnector: UserDetailsConnector = UserDetailsConnector

  lazy val userDetails = currentCoAFEAuthority.flatMap { authority =>
    userDetailsConnector.getUserDetails(authority.userDetailsLink)
  }

  lazy val activeEnrolmentKeys = activeEnrolments.map(enrList => enrList.map(_.key).toSet[String])

  lazy val activeEnrolments = enrolments.map { enrolmentSeq =>
    enrolmentSeq.filter(_.state == EnrolmentState.ACTIVATED)
  }

  lazy val notActivatedEnrolmentKeys = enrolments.map { enrolmentSeq =>
    enrolmentSeq.filter(_.state != EnrolmentState.ACTIVATED).map(_.key).toSet[String]
  }

  lazy val lastSaReturn = authContext.principal.accounts.sa
    .fold(Future(SaReturn.empty))(saAccount => selfAssessmentConnector.lastReturn(saAccount.utr.value))

  lazy val currentCoAFEAuthority = frontendAuthConnector.currentCoAFEAuthority()

  lazy val internalUserIdentifier = currentCoAFEAuthority.map(_.internalUserIdentifier)

  lazy val enrolments = currentCoAFEAuthority.flatMap { authority =>
    lazy val noEnrolments = Future.successful(Seq.empty[GovernmentGatewayEnrolment])
    authority.enrolmentsUri.fold(noEnrolments)(uri => frontendAuthConnector.getEnrolments(uri))
  }

  lazy val affinityGroup = userDetails.map(_.affinityGroup)

  lazy val isAdmin = userDetails.map(_.isAdmin)
}
