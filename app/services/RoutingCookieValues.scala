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

import org.joda.time.{DateTime, DateTimeZone}

trait DocumentExpirationTime {
  def getExpirationTime: DateTime
}

case class Duration(seconds: Int) extends DocumentExpirationTime {
  override def getExpirationTime: DateTime = DateTime.now().withZone(DateTimeZone.UTC).plusSeconds(seconds)
}

case class Instant(expirationTime: DateTime) extends DocumentExpirationTime {
  override def getExpirationTime: DateTime = expirationTime
}

case class RoutingCookieValues(routedDestination: String, throttledDestination: String) {

  override def toString = s"$routedDestination#$throttledDestination"

}

object RoutingCookieValues {

  def apply(cookieValue: String): RoutingCookieValues = {
    val tokens = cookieValue.split("#")
    RoutingCookieValues(tokens(0), tokens(1))
  }

}

object CookieNames {

  val mdtpRouting = "mdtprouting"

}
