/*
 * Copyright 2017 HM Revenue & Customs
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

object Throttler {

  private val percentageDivisor = 100

  def shouldThrottle(discriminator: String, percentageToBeThrottled: Int) = {

    implicit class MD5(value: String) {
      def toMD5 = {
        val md5 = MessageDigest.getInstance("MD5")
        md5.digest(value.getBytes).map("%02x".format(_)).mkString
      }
    }

    val percentage = Math.abs((discriminator.toMD5.hashCode % percentageDivisor).toDouble)
    percentage <= percentageToBeThrottled
  }
}
