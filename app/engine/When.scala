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

package engine

import model.{Location, RuleContext, TAuditContext}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

case class When(condition: Condition) {

  def thenGoTo(location: Location): Rule = new Rule {
    override def apply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Option[Location]] =
      condition.evaluate(authContext, ruleContext, auditContext) map {
        case true => Some(location)
        case false => None
      }

    override val name: String = "none"
  }
}
