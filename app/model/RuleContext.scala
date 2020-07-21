/*
 * Copyright 2020 HM Revenue & Customs
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
import javax.inject.{Inject, Singleton}
import play.api.mvc.{AnyContent, Request}
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RuleContext @Inject()(val authConnector: AuthConnector,
                            selfAssessmentConnector: SelfAssessmentConnector)(implicit request: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext)
  extends AuthorisedFunctions {

  val request_ : Request[AnyContent] = request
  val hc_ : HeaderCarrier = hc

  val logger: LoggerLike = Logger

  lazy val isVerifyUser: Future[Boolean] = {
    authorised(AuthProviders(AuthProvider.Verify)){ Future.successful(true) }.recover {case _ => false}
  }

  lazy val isGovernmentGatewayUser: Future[Boolean] = {
    authorised(AuthProviders(AuthProvider.GovernmentGateway)){ Future.successful(true) }.recover {case _ => false}
  }

  lazy val hasNino: Future[Boolean] = {
    authorised().retrieve(Retrievals.nino){
      case Some(nino) => Future.successful(true)
      case _          => Future.successful(false)
    }
  }

  lazy val activeEnrolmentKeys: Future[Set[String]] = activeEnrolments.map(enrList => enrList.map(_.key).toSet[String])

  lazy val activeEnrolments: Future[Set[Enrolment]] = enrolments.map { enrolmentSeq =>
    enrolmentSeq.enrolments.filter(_.state == EnrolmentState.ACTIVATED)
  }

  lazy val notActivatedEnrolmentKeys: Future[Set[String]] = enrolments.map { enrolmentSeq =>
    enrolmentSeq.enrolments.filter(_.state != EnrolmentState.ACTIVATED).map(_.key).toSet[String]
  }

  lazy val lastSaReturn: Future[SaReturn] =
    authorised().retrieve(Retrievals.saUtr) {
      case Some(saUtr) => selfAssessmentConnector.lastReturn(saUtr)
      case _ => Future(SaReturn.empty)
    }

  lazy val internalUserIdentifier: Future[Option[String]] =
    authorised().retrieve(Retrievals.internalId) {
      case internalId => Future.successful(internalId)
      case _ => Future.successful(None)
    }

  lazy val enrolments: Future[Enrolments] =
    authorised().retrieve(Retrievals.allEnrolments) {
      case enrolments => Future.successful(enrolments)
      case _ => Future.successful(Enrolments(Set.empty[Enrolment]))
    }

  lazy val affinityGroup: Future[String] = {
    authorised().retrieve(Retrievals.affinityGroup){
      case Some(affinityGroup) => Future.successful(affinityGroup.toString)
      case _ => Future.failed(new RuntimeException("affinityGroup is not defined"))
    }
  }

}
