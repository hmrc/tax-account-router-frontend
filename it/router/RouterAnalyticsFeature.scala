package router

import connector.{AnalyticsData, GaEvent}
import org.scalatest.concurrent.ScalaFutures
import play.api.test.WsTestClient
import support.stubs.{CommonStubs, StubbedFeatureSpec}


class RouterAnalyticsFeature extends StubbedFeatureSpec with CommonStubs with WsTestClient with ScalaFutures {

  feature("Router analytics feature") {

    scenario("check service is reachable") {
      WsTestClient.withClient { client ⇒
        client.url(s"http://localhost:$port/ping/ping").get().futureValue.status shouldBe 200
      }
    }

    scenario("send analytics details") {

      Given("a user logged in through Government Gateway and the user has self assessment enrolments")
      setGGUser()

      And("the user has no previous returns")
      stubRetrievalSAUTR()
      stubSaReturnWithNoPreviousReturns(saUtr)

      And("User has google analytics cookie in browser")
      When("the user hits the router")

      withClient{ wsClient =>
        wsClient
          .url(s"http://localhost:$port/account")
          .withHttpHeaders("Cookie" -> """_ga=GA1.4.405633776.1470748420""")
          .get().futureValue.status shouldBe 404
      }

      And("analytic details were sent to google")

      verifyAnalytics(
        AnalyticsData("GA1.4.405633776.1470748420", List(
          GaEvent("routing", "business-tax-account", "bta-home-page-for-user-with-no-previous-return", Nil)
        ))
      )
    }
  }
}
