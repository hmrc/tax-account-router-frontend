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

import play.api.Play
import play.api.Play.current

trait BaseEnrolmentRule extends ((Set[String]) => Boolean)


trait EnrolmentRule extends BaseEnrolmentRule {
  def enrolmentKey: String

  lazy val enrolments: Set[String] = Play.configuration.getStringSeq(enrolmentKey).getOrElse(Seq()).toSet[String]

  override def apply(activeEnrolmentKeys: Set[String]): Boolean = {
    activeEnrolmentKeys.intersect(enrolments).nonEmpty
  }
}

object WithBusinessEnrolmentsRule extends EnrolmentRule {
  val enrolmentKey = "business-enrolments"
}

object WithSAEnrolmentRule extends EnrolmentRule {
  val enrolmentKey = "self-assessment-enrolments"
}

object WithInPartnershipEnrolmentRule extends EnrolmentRule {
  val enrolmentKey = "partnership-enrolments"
}

object WithSelfEmployeeEnrolmentRule extends EnrolmentRule {
  val enrolmentKey = "self-employed-enrolments"
}

object WithSAAndInPartnershipEnrolmentRule extends BaseEnrolmentRule {

  override def apply(activeEnrolmentKeys: Set[String]): Boolean = {
    WithSAEnrolmentRule(activeEnrolmentKeys) && WithInPartnershipEnrolmentRule(activeEnrolmentKeys)
  }

}

object WithSAAnSelfEmployeeEnrolmentRule extends BaseEnrolmentRule {

  override def apply(activeEnrolmentKeys: Set[String]): Boolean = {
    WithSAEnrolmentRule(activeEnrolmentKeys) && WithSelfEmployeeEnrolmentRule(activeEnrolmentKeys)
  }

}
