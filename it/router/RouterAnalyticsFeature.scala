package router

import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import connector.{AnalyticsData, AnalyticsPlatformConnector, GaEvent}
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.{GivenWhenThen, MustMatchers, WordSpec}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.test.WsTestClient
import support.TARIntegrationTest
import support.stubs.stubs.StubAnalyticsPlatformConnector
import support.stubs.{CommonStubs, StubHelper}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class RouterAnalyticsFeature extends WordSpec with MustMatchers with TARIntegrationTest with GivenWhenThen with CommonStubs with StubHelper with WsTestClient {

  lazy val connector: AnalyticsPlatformConnector = inject[AnalyticsPlatformConnector]
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testData = AnalyticsData("GA1.4.405633776.1470748420",
    List(GaEvent("routing", "business-tax-account", "bta-home-page-for-user-with-no-previous-return", Nil)))

  val postJson = """{
               |  "gaClientId" : "GA1.4.405633776.1470748420",
               |  "events" : [
               |    {
               |      "category": "routing",
               |      "action": "business-tax-account",
               |      "label": "bta-home-page-for-user-with-no-previous-return",
               |      "dimensions": []
               |    }
               |  ]
               |}""".stripMargin

  "AnalyticsPlatformConnector" when {
      "send a GA event to platform-analytics with the correct json " in {
        setGGUser()
        stubRetrievalSAUTR()
        stubSaReturnWithNoPreviousReturns(saUtr)
        StubAnalyticsPlatformConnector.stubAnalyticsPost(postJson)(200, None)
        stubGet("/business-account", 404, None)

        withClient{ wsClient =>
          val result = await(wsClient
            .url(s"http://localhost:$port/account")
            .withHttpHeaders("Cookie" -> """_ga=GA1.4.405633776.1470748420""")
            .get())
          result.status shouldBe 404
        }

        verifyAnalytics(testData)
      }
    }
}
