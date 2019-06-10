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

package model

import connector._
import play.api.mvc.{AnyContent, Request}
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


case class RuleContext(credId: Option[String])(implicit request: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext) {

  val request_ = request
  val hc_ = hc

  val logger: LoggerLike = Logger

  val selfAssessmentConnector: SelfAssessmentConnector = SelfAssessmentConnector
  val authConnector: FrontendAuthConnector = FrontendAuthConnector
  val userDetailsConnector: UserDetailsConnector = UserDetailsConnector

  lazy val userDetails = authority.flatMap { authority =>
    authority.userDetailsUri.map(userDetailsConnector.getUserDetails).getOrElse {
      logger.warn("failed to get user details because userDetailsUri is not defined")
      Future.failed(new RuntimeException("userDetailsUri is not defined"))
    }
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
    case Some(aCredId) => authConnector.userAuthority(aCredId)
    case None => authConnector.currentUserAuthority
  }

  lazy val internalUserIdentifier = authority.flatMap { authority =>
    authority.idsUri match {
      case Some(uri) => authConnector.getIds(uri).map(Option(_))
      case _ => Future.successful(None)
    }
  }


  lazy val enrolments = authority.flatMap { authority =>
    lazy val noEnrolments = Future.successful(Seq.empty[GovernmentGatewayEnrolment])
    authority.enrolmentsUri.fold(noEnrolments)(authConnector.getEnrolments)
  }

  lazy val affinityGroup = userDetails.map(_.affinityGroup)

  lazy val isAdmin = userDetails.map(_.isAdmin)
}
