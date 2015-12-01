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

import controllers.ExternalUrls
import model.LocationGroup.LocationCategoryType

object Location extends Enumeration {

  type LocationType = Type

  case class Type(url: String, name: String, group: LocationCategoryType) extends Val

  // TODO: this enum should be designed so that each location name is unique

  val PersonalTaxAccount = Type(ExternalUrls.personalTaxAccountUrl, "personal-tax-account", LocationGroup.PTA)
  val BusinessTaxAccount = Type(ExternalUrls.businessTaxAccountUrl, "business-tax-account", LocationGroup.BTA)

  val locations: Map[String, LocationType] = Location.values.toList.map(_.asInstanceOf[LocationType]).map(value => value.name -> value).toMap

}

object LocationGroup extends Enumeration {

  type LocationCategoryType = Type

  case class Type(name: String) extends Val

  val PTA = Type("PTA")
  val BTA = Type("BTA")

}
