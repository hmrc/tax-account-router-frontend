package router

import org.scalatest.{FeatureSpec, GivenWhenThen, MustMatchers}
import play.api.libs.ws.WSResponse
import support.TARIntegrationTest
import support.TARIntegrationTestClient.TARRoutes

class RouterAnalyticsFeature extends FeatureSpec with MustMatchers with TARIntegrationTest with GivenWhenThen{

  def gotoHomePage(): WSResponse = TARRoutes.get("/")


  feature("Router analytics feature") {

    scenario("check service is reachable") {

      val result = gotoHomePage()

      result.status mustBe 200

//      WsTestClient.withClient { client ⇒
//        client.url(s"http://localhost:$port/ping/ping").get().futureValue.status shouldBe 200

    }

//    scenario("send analytics details") {
//
//      Given("a user logged in through Government Gateway and the user has self assessment enrolments")
//      setGGUser()
//
//      And("the user has no previous returns")
//      stubRetrievalSAUTR()
//      stubSaReturnWithNoPreviousReturns(saUtr)
//
//      And("User has google analytics cookie in browser")
//      When("the user hits the router")
//
//      withClient{ wsClient =>
//        wsClient
//          .url(s"http://localhost:$port/account")
//          .withHttpHeaders("Cookie" -> """_ga=GA1.4.405633776.1470748420""")
//          .get().futureValue.status shouldBe 404
//      }
//
//      And("analytic details were sent to google")
//
//      verifyAnalytics(
//        AnalyticsData("GA1.4.405633776.1470748420", List(
//          GaEvent("routing", "business-tax-account", "bta-home-page-for-user-with-no-previous-return", Nil)
//        ))
//      )
//    }
  }
}
