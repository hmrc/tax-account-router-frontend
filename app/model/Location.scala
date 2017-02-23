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

package model

import model.TLocation.LocationBuilder
import play.api.Configuration

case class Location(name: String, url: String)

object TLocation {

  import cats.instances.all._
  import cats.syntax.flatMap._
  import cats.syntax.functor._

  type LocationBuilder = (Configuration) => Either[String, Location]

  def apply(locationName: String): LocationBuilder = {

    def getString(key: String): (Configuration) => Either[String, String] = conf =>
      conf.getString(s"locations.$locationName.$key")
        .map(Right(_))
        .getOrElse(Left(s"key '$key' not configured for location '$locationName'"))

    for {
      name <- getString("name")
      url <- getString("url")
    } yield for {
      n <- name.right
      u <- url.right
    } yield Location(n, u)
  }
}

object Locations {

  import play.api.Play._

  def buildLocation(locationBuilder: LocationBuilder): Location =
    locationBuilder(configuration).fold[Location](
      error => throw new RuntimeException(error),
      identity
    )

  val PersonalTaxAccount = buildLocation(TLocation("pta"))
  val BusinessTaxAccount = buildLocation(TLocation("bta"))
  val TaxAccountRouterHome = buildLocation(TLocation("tax-account-router"))

  val all = List(PersonalTaxAccount, BusinessTaxAccount, TaxAccountRouterHome)

  def find(name: String): Option[Location] = all.find { case Location(n, _) => n == name }
}
