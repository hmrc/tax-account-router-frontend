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
import org.openqa.selenium.By
import play.api.test.FakeApplication
import support.page._
import support.stubs.{CommonStubs, StubbedFeatureSpec, TaxAccountUser}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, SaAccount}

trait WelcomePageStubs extends CommonStubs {

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

  val enrolmentConfiguration = Map[String, Any](
        "business-enrolments" -> List("enr1", "enr2"),
        "self-assessment-enrolments" -> List("enr3", "enr4")
      )

  override lazy val app = FakeApplication(additionalConfiguration = config ++ enrolmentConfiguration)

  feature("BTA Welcome page") {

    scenario("is shown only when a user logs in for the first time") {

      Given("a new user")
      createStubs(TaxAccountUser(firstTimeLoggedIn = true))

      And("the welcome page has never been visited")
      stubSave4LaterToBeEmpty()
      stubSaveForLaterPUT()

      And("the user profile has a business related enrolment")
      stubGovernmentGatewayProfileWithBusinessEnrolment()

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be redirected to the Welcome page")
      on(BusinessWelcomePage)

      And("the welcomePageSeen flag should be set to 'true'")
      verify(putRequestedFor(urlEqualTo("/save4later/business-tax-account/1234567890/data/welcomePageSeen")))

      Given("the Welcome page has already been visited")
      stubSave4LaterWelcomePageSeen()

      And("a stubbed BTA homepage")
      createStubs(BtaHomeStubPage)

      When("the user clicks on Continue on the Welcome page")
      BusinessWelcomePage.clickContinue()

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

  feature("PTA Welcome page") {

    scenario("is shown only when a user logs in for the first time") {

      Given("a new user")
      val saUtr = "12345"
      val accounts = Accounts(sa = Some(SaAccount("", SaUtr(saUtr))))
      createStubs(TaxAccountUser(firstTimeLoggedIn = true, accounts = accounts))

      And("the welcome page has never been visited")
      stubSave4LaterToBeEmpty()
      stubSaveForLaterPUT()

      And("the user has self assessment enrolments")
      stubProfileWithSelfAssessmentEnrolments()

      And("the user has business related enrolments")
      stubSaReturn(saUtr = saUtr, previousReturns = true)

      When("the user hits the router")
      go(RouterRootPath)

      Then("the user should be redirected to the Welcome page")
      on(PersonalWelcomePage)

      And("the welcomePageSeen flag should be set to 'true'")
      verify(putRequestedFor(urlEqualTo("/save4later/business-tax-account/1234567890/data/welcomePageSeen")))

      Given("the Welcome page has already been visited")
      stubSave4LaterWelcomePageSeen()

      And("a stubbed PTA homepage")
      createStubs(PtaHomeStubPage)

      When("the user clicks on Continue on the Welcome page")
      PersonalWelcomePage.clickContinue()

      Then("the user should be redirected to BTA home page")
      on(PtaHomePage)

      And("the user profile should be fetched from the Government Gateway")
      verify(getRequestedFor(urlEqualTo("/profile")))

      When("the user hits directly the router again")
      go(RouterRootPath)

      Then("the user should be redirected to BTA home page")
      on(PtaHomePage)
    }
  }

  scenario("has a Home button that redirects to the Router root path") {

    Given("a new user")
    createStubs(TaxAccountUser(firstTimeLoggedIn = true))

    And("the welcome page has never been visited")
    stubSave4LaterToBeEmpty()
    stubSaveForLaterPUT()

    And("the user profile has a business related enrolment")
    stubGovernmentGatewayProfileWithBusinessEnrolment()

    And("the user navigates to the Welcome page")
    go(BusinessWelcomePage)

    And("the Welcome page has already been visited")
    stubSave4LaterWelcomePageSeen()

    And("the user is supposed to be routed to BTA")
    createStubs(BtaHomeStubPage)

    When("the user clicks on Home on the Welcome page")
    val homeUrl = webDriver.findElement(By.id("homeNavHref")).getAttribute("href")
    webDriver.get(homeUrl)

    And("the user should be redirected to BTA home page")
    on(BtaHomePage)
  }
}
