/*
 * Copyright 2023 HM Revenue & Customs
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

package actions

import config.FrontendAppConfig
import model.UserProfile
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class AuthAction @Inject()(val config: FrontendAppConfig, val authConnector: AuthConnector) extends AuthorisedFunctions {

  def userProfile(body: UserProfile => Future[Result])
                 (implicit ex: ExecutionContext, hc: HeaderCarrier): Future[Result] = {
    authorised()
      .retrieve(allEnrolments and affinityGroup and confidenceLevel
        and credentialRole and credentialStrength and credentials and groupIdentifier){
        case enrolments ~ affinityGroup ~ confidenceLevel ~ credentialRole ~ credentialStrength ~ credentials ~ groupId =>
          body(UserProfile(enrolments.enrolments, affinityGroup, confidenceLevel, credentialRole, credentialStrength, credentials, groupId))
      } recover {
      case _: AuthorisationException =>
        Redirect(config.signIn)
    }
  }

}
