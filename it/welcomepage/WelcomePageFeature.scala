package welcomepage

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

import com.github.tomakehurst.wiremock.client.WireMock.{findAll => wmFindAll, _}
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}

trait WelcomePageStubs extends CommonStubs {

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

      Given("a new BTA user")
      createStubs(TaxAccountUser(firstTimeLoggedIn = true))

      And("the welcome page has never been visited")
      stubSave4LaterToBeEmpty()
      stubSaveForLaterPUT()

      And("the user profile has a business related enrolment")
      stubGovernmentGatewayProfileWithBusinessEnrolment()

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be redirected to the Welcome page")
      on(WelcomePage)

      And("the welcomePageSeen flag should be set to 'true'")
      verify(putRequestedFor(urlEqualTo("/save4later/business-tax-account/1234567890/data/welcomePageSeen")))

      Given("the Welcome page has already been visited")
      stubSave4LaterWelcomePageSeen()

      And("a stubbed BTA homepage")
      createStubs(BtaHomeStubPage)

      When("the user clicks on Continue on the Welcome page")
      WelcomePage.clickContinue()

      Then("the user should be redirected to BTA home page")
      on(BtaHomePage)

      And("the user profile should be fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))

      When("the user hits directly the router again")
      go(RouterRootPath)

      Then("the user should be redirected to BTA home page")
      on(BtaHomePage)
    }
  }
}
