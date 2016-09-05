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
  val businessTaxAccountLocationName = "business-tax-account"
  val taxAccountRouterHomeLocationName = "tax-account-router"
  val userDelegationLocationName = "user-delegation-frontend"
  val areYouSharingLocationName = "are-you-sharing"

  lazy val PersonalTaxAccount = Location(personalTaxAccountLocationName, ExternalUrls.getUrl(personalTaxAccountLocationName))
  lazy val BusinessTaxAccount = Location(businessTaxAccountLocationName, ExternalUrls.getUrl(businessTaxAccountLocationName))
  lazy val SetUpExtraSecurity = Location(userDelegationLocationName, s"${ExternalUrls.getUrl(userDelegationLocationName)}/set-up-extra-security")
  lazy val AreYouSharing = Location(userDelegationLocationName, s"${ExternalUrls.getUrl(userDelegationLocationName)}/are-you-sharing")
  lazy val TaxAccountRouterHome = Location(taxAccountRouterHomeLocationName, s"${ExternalUrls.taxAccountRouterHost}/account")
  val twoStepVerificationLocationName = "two-step-verification"
  val twoStepVerificationRequiredLocationName = "two-step-verification-required"

  def twoStepVerification(queryString: Map[String, String]) = Location(twoStepVerificationLocationName, ExternalUrls.getUrl(twoStepVerificationLocationName), queryString)

  def twoStepVerificationRequired(queryString: Map[String, String]) = Location(twoStepVerificationLocationName, ExternalUrls.getUrl(twoStepVerificationRequiredLocationName), queryString)

  lazy val all = List(PersonalTaxAccount, BusinessTaxAccount)

  def find(name: String) = all.find(_.name == name)
}
