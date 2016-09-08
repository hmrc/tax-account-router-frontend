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

package model

import java.net.{URI, URLEncoder}

import controllers.ExternalUrls
import play.api.Play._

case class Location(name: String, url: String, queryParams: Map[String, String] = Map.empty[String, String]) {

  lazy val fullUrl = {
    def encode(value: String) = URLEncoder.encode(value, "utf-8")

    def urlWithQueryParams =
      if (queryParams.isEmpty) url
      else {
        val encoded = for {
          (key, value) <- queryParams
        } yield s"${encode(key)}=${encode(value)}"

        s"$url?${encoded.mkString("&")}"
      }

    URI.create(urlWithQueryParams).toString
  }
}

object Locations {
  val personalTaxAccountLocationName = "personal-tax-account"

  lazy val PersonalTaxAccount = Location(personalTaxAccountLocationName, ExternalUrls.getUrl(personalTaxAccountLocationName))
  lazy val BusinessTaxAccount = locationFromConf("bta")
  lazy val TaxAccountRouterHome = locationFromConf("tax-account-router")
  val twoStepVerificationLocationName = "two-step-verification"
  val twoStepVerificationRequiredLocationName = "two-step-verification-required"

  def locationFromConf(location: String) = configuration.getConfig(s"locations.$location").map { conf =>
    val name = conf.getString("name").getOrElse(throw new RuntimeException(s"name not configured for location - $location"))
    val url = conf.getString("url").getOrElse(throw new RuntimeException(s"url not configured for location - $location"))
    val queryParams = conf.getConfig("queryparams").map { queryParamsConf =>
      queryParamsConf.entrySet.foldLeft(Map.empty[String, String]) {
        case (result, (key, value)) => result ++ Map(key -> value.unwrapped().asInstanceOf[String])
      }
    }.getOrElse(Map.empty[String, String])
    Location(name, url, queryParams)
  }.getOrElse(throw new RuntimeException(s"location configuration not defined for $location"))


  def twoStepVerificationRequired(queryString: Map[String, String]) = Location(twoStepVerificationLocationName, ExternalUrls.getUrl(twoStepVerificationRequiredLocationName), queryString)

  lazy val all = List(PersonalTaxAccount, BusinessTaxAccount)

  def find(name: String) = all.find(_.name == name)
}
