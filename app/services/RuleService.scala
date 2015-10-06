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

package services

import model.{Location, Rule, RuleContext}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

trait RuleService {

  def fireRules(rules: List[Rule])(implicit user: AuthContext, request: Request[AnyContent], hc: HeaderCarrier, ruleContext: RuleContext): Future[Option[Location]] = {
    rules.foldLeft(Future[Option[Location]](None)) {
      (location, rule) => location.flatMap(candidateLocation => if (candidateLocation.isDefined) location else rule.apply)
    }
  }

}

object RuleService extends RuleService
