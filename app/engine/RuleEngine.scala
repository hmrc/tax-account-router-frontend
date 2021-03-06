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

package engine

import cats.data.WriterT
import model._
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

trait RuleEngine {

  def rules(implicit request: Request[AnyContent], hc: HeaderCarrier): List[Rule[RuleContext]]

  def defaultLocation: Location

  def defaultRuleName: String

  def getLocation(ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): EngineResult = {
    rules.foldLeft(emptyRuleResult) { (result, rule) =>
      result.flatMap {
        case someLocation@Some(_) => WriterT(result.written.map(auditInfo => (auditInfo, someLocation)))
        case _ => rule.evaluate(ruleContext)
      }
    } mapBoth { (auditInfo, maybeLocation) =>
      maybeLocation match {
        case Some(location) => (auditInfo, location)
        case _ => (auditInfo.copy(ruleApplied = Some(defaultRuleName)), defaultLocation)
      }
    }
  }
}
