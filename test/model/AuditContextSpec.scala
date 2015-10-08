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

import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuditContextSpec extends UnitSpec {

  "default reasons" should {

    "be the expected ones" in {

      AuditContext.defaultReasons shouldBe Map(
        "has-seen-welcome-page" -> "-",
        "has-print-preferences-set" -> "-",
        "has-business-enrolments" -> "-",
        "has-previous-returns" -> "-",
        "is-in-a-partnership" -> "-",
        "is-self-employed" -> "-"
      )
    }
  }

  "audit context" should {

    "set reasons values" in {
      val auditContext: TAuditContext = AuditContext()

      val hasSeenWelcomePage: Future[Boolean] = auditContext.setHasSeenWelcomePage(Future(true))
      val hasPrintPreferencesSet: Future[Boolean] = auditContext.setHasPrintPreferencesSet(Future(true))
      val hasPreviousReturns: Future[Boolean] = auditContext.setHasPreviousReturns(Future(true))
      val hasBusinessEnrolments: Future[Boolean] = auditContext.setHasBusinessEnrolments(Future(true))
      val isInAPartnership: Future[Boolean] = auditContext.setIsInAPartnership(Future(true))
      val isSelfEmployed: Future[Boolean] = auditContext.setIsSelfEmployed(Future(true))

      val expectedReasons = Map(
        "has-seen-welcome-page" -> "true",
        "has-print-preferences-set" -> "true",
        "has-business-enrolments" -> "true",
        "has-previous-returns" -> "true",
        "is-in-a-partnership" -> "true",
        "is-self-employed" -> "true"
      )

      val result = for {
        welcomePageSeen <- hasSeenWelcomePage
        printPreferencesSet <- hasPrintPreferencesSet
        previousReturns <- hasPreviousReturns
        businessEnrolments <- hasBusinessEnrolments
        partnership <- isInAPartnership
        selfEmployed <- isSelfEmployed
      } yield selfEmployed

      await(result)

      auditContext.asInstanceOf[AuditContext].reasons.toMap[String, String] shouldBe expectedReasons
    }
  }
}
