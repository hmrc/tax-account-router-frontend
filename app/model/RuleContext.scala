/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RuleContext @Inject()(val authConnector: AuthConnector,
                            selfAssessmentConnector: SelfAssessmentConnector)(implicit ec: ExecutionContext)
  extends AuthorisedFunctions {

  val logger: LoggerLike = Logger

  def isVerifyUser(implicit hc: HeaderCarrier): Future[Boolean] = {
    authorised(AuthProviders(AuthProvider.Verify)){
      Future.successful(true)
    }.recover {case _ => false}
  }

  def isGovernmentGatewayUser(implicit hc: HeaderCarrier): Future[Boolean] = {
    authorised(AuthProviders(AuthProvider.GovernmentGateway)){
      Future.successful(true)
    }.recover {case _ => false}
  }

  def hasNino(implicit hc: HeaderCarrier): Future[Boolean] = {
    authorised().retrieve(Retrievals.nino){
      case Some(_)    => Future.successful(true)
      case _          => Future.successful(false)
    }.recover {case _ => false}
  }

  def activeEnrolmentKeys(implicit hc: HeaderCarrier): Future[Set[String]] = activeEnrolments.map(enrList => enrList.map(_.key))

  def activeEnrolments(implicit hc: HeaderCarrier): Future[Set[Enrolment]] = enrolments.map { enrolmentSeq =>
    enrolmentSeq.enrolments.filter(_.state == EnrolmentState.ACTIVATED)
  }.recover { case _ => Set.empty[Enrolment]}

  def notActivatedEnrolmentKeys(implicit hc: HeaderCarrier): Future[Set[String]] = enrolments.map { enrolmentSeq =>
    enrolmentSeq.enrolments.filter(_.state != EnrolmentState.ACTIVATED).map(_.key)
  }.recover { case _ => Set.empty[String]}

  def lastSaReturn(implicit hc: HeaderCarrier): Future[SaReturn] =
    authorised().retrieve(Retrievals.saUtr) {
      case Some(saUtr) => selfAssessmentConnector.lastReturn(saUtr)
      case _ => Future(SaReturn.empty)
    }.recover {case _ => SaReturn.empty}

  def internalUserIdentifier(implicit hc: HeaderCarrier): Future[Option[String]] =
    authorised().retrieve(Retrievals.internalId)(Future.successful)
      .recover {case _ => None}

  def enrolments(implicit hc: HeaderCarrier): Future[Enrolments] =
    authorised().retrieve(Retrievals.allEnrolments)(Future.successful)
      .recoverWith {case _ => Future.failed(new RuntimeException("gg-unavailable"))}

  def affinityGroup(implicit hc: HeaderCarrier): Future[String] = {
    authorised().retrieve(Retrievals.affinityGroup){
      case Some(affinityGroup) => Future.successful(affinityGroup.toString)
      case _ => Future.failed(new RuntimeException("affinityGroup is not defined"))
    }
  }

}
