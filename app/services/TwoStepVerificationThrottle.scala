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

import java.security.MessageDigest

import org.joda.time.DateTime
import play.api.Logger
import play.api.Play.{current, _}

trait TwoStepVerificationThrottle {
  private val bucketSize = 100
  private val decimalPointFactor = 10

  def timeBasedLimit: TimeBasedLimit

  def isRegistrationMandatory(ruleName: String, discriminator: String) = {
    val userValue = Math.abs((discriminator.toMD5.hashCode % (bucketSize * decimalPointFactor)).toDouble) / decimalPointFactor
    val threshold = timeBasedLimit.getCurrentPercentageLimit(ruleName)
    Logger.info(s"Threshold: $threshold - userValue: $userValue")
    userValue <= threshold
  }

  implicit class MD5(value: String) {
    def toMD5 = {
      val md5 = MessageDigest.getInstance("MD5")
      md5.digest(value.getBytes).map("%02x".format(_)).mkString
    }
  }
}

object TwoStepVerificationThrottle extends TwoStepVerificationThrottle {
  override lazy val timeBasedLimit = TimeBasedLimit
}

trait TimeBasedLimit {
  def dateTimeProvider: () => DateTime

  def getCurrentPercentageLimit(ruleName: String) = {
    val currentHourOfDay = dateTimeProvider().getHourOfDay
    val hourlyLimit = configuration.getDouble(s"two-step-verification.user-segment.$ruleName.throttle.$currentHourOfDay")
    hourlyLimit.getOrElse(configuration.getDouble(s"two-step-verification.user-segment.$ruleName.throttle.default").getOrElse(-1.0))
  }
}

object TimeBasedLimit extends TimeBasedLimit {
  override def dateTimeProvider = () => DateTime.now()
}