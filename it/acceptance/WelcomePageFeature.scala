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

package acceptance

import com.github.tomakehurst.wiremock.client.WireMock.{findAll => wmFindAll, _}
import support.page.{RouterHomePage, WelcomePage, YtaHomePage, YtaHomeStubPage}
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}

trait WelcomePageStubs extends CommonStubs {

  def stubSave4LaterToBeEmpty() =
    stubFor(get(urlMatching("/save4later/business-tax-account/.*"))
      .willReturn(aResponse()
      .withStatus(404)))

  def stubSaveForLaterPUT() =
    stubFor(put(urlMatching("/save4later/business-tax-account/.*/data/welcomePageSeen"))
      .willReturn(aResponse()
      .withStatus(200)
      .withBody( """
                   |{
                   |    "id": "some-session-id",
                   |    "data": {
                   |        "welcomePageSeen": true
                   |    }
                   |}
                   | """.stripMargin)))

  def stubGovernmentGatewayProfileWithBusinessEnrolment() =
    stubFor(get(urlMatching("/profile"))
      .willReturn(aResponse()
      .withStatus(200)
      .withBody( """
                   |{
                   |  "affinityGroup": "Organisation",
                   |  "enrolments": [{"key": "enr1", "identifier": "5597800686", "state": "Activated"}]
                   |}
                   | """.stripMargin)))

}

class WelcomePageFeature extends StubbedFeatureSpec with WelcomePageStubs {

  feature("Welcome page") {

    scenario("is shown only once") {

      Given("I am a new YTA user")
      createStubs(TaxAccountUser(firstTimeLoggedIn = true))

      And("There is nothing in Save4Later for the session")
      stubSave4LaterToBeEmpty()
      stubSaveForLaterPUT()

      And("The user profile has a business related enrolment")
      stubGovernmentGatewayProfileWithBusinessEnrolment()

      When("I login for the first time")
      go(RouterHomePage)

      Then("I am on the welcome page")
      on(WelcomePage)

      And("the welcomePageSeen flag has been set to 'true'")
      verify(putRequestedFor(urlEqualTo("/save4later/business-tax-account/1234567890/data/welcomePageSeen")))

      And("The Save4Later stub is updated to return the new welcomePageSeen flag")
      stubSave4LaterWelcomePageSeen()

      createStubs(YtaHomeStubPage)

      And("I press continue on the welcome page")
      WelcomePage.clickContinue()

      Then("I am on the YTA Home Page")
      on(YtaHomePage)

      And("the user profile has been fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))
    }
  }
}
