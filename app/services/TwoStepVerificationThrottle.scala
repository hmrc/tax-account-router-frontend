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

package services

import org.joda.time.DateTime
import play.api.Play.{current, _}

trait TwoStepVerificationThrottle {
  def timeBasedLimit: TimeBasedLimit

  def registrationMandatory(discriminator: String) = {
    val userValue = Math.abs(discriminator.hashCode % 100)
    val threshold = timeBasedLimit.getCurrentPercentageLimit
    userValue <= threshold
  }
}

object TwoStepVerificationThrottle extends TwoStepVerificationThrottle {
  override lazy val timeBasedLimit = TimeBasedLimit
}


trait TimeBasedLimit {
  def dateTimeProvider: () => DateTime

  def getCurrentPercentageLimit = {
    val currentHourOfDay = dateTimeProvider().getHourOfDay
    val hourlyLimit = configuration.getInt(s"two-step-verification.throttle.$currentHourOfDay")
    val defaultLimit = configuration.getInt("two-step-verification.throttle.default")
    hourlyLimit.orElse(defaultLimit).getOrElse(0)
  }
}

object TimeBasedLimit extends TimeBasedLimit {
  override def dateTimeProvider = () => DateTime.now()
}