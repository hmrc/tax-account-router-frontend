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
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.CredentialStrength
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

// TODO: option of credId seems to be ugly here, we can do better. I'd rather pass the authority object!
case class RuleContext(credId: Option[String])(implicit hc: HeaderCarrier) {
  val selfAssessmentConnector: SelfAssessmentConnector = SelfAssessmentConnector
  val authConnector:FrontendAuthConnector = FrontendAuthConnector
  val userDetailsConnector: UserDetailsConnector = UserDetailsConnector

  lazy val userDetails = authority.flatMap { authority =>
    userDetailsConnector.getUserDetails(authority.userDetailsLink)
  }

  lazy val activeEnrolmentKeys = activeEnrolments.map(enrList => enrList.map(_.key).toSet[String])

  lazy val activeEnrolments = enrolments.map { enrolmentSeq =>
    enrolmentSeq.filter(_.state == EnrolmentState.ACTIVATED)
  }

  lazy val notActivatedEnrolmentKeys = enrolments.map { enrolmentSeq =>
    enrolmentSeq.filter(_.state != EnrolmentState.ACTIVATED).map(_.key).toSet[String]
  }

  lazy val lastSaReturn = authority.flatMap { auth => auth.saUtr.fold(Future(SaReturn.empty))(saUtr => selfAssessmentConnector.lastReturn(saUtr.utr)) }

  lazy val authority = credId match {
    case Some(credId) => authConnector.tarAuthority(credId)
    case None => authConnector.currentTarAuthority
  }

  lazy val internalUserIdentifier = authority.flatMap(auth => authConnector.getIds(auth.idsUri))

  lazy val enrolments = authority.flatMap { authority =>
    lazy val noEnrolments = Future.successful(Seq.empty[GovernmentGatewayEnrolment])
    authority.enrolmentsUri.fold(noEnrolments)(authConnector.getEnrolments)
  }

  lazy val affinityGroup = userDetails.map(_.affinityGroup)

  lazy val isAdmin = userDetails.map(_.isAdmin)

}
