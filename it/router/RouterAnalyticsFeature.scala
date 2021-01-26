package router

import connector.{AnalyticsData, AnalyticsPlatformConnector, GaEvent}
import org.scalatest.{GivenWhenThen, MustMatchers, WordSpec}
import support.TARIntegrationTest
import support.stubs.stubs.StubAnalyticsPlatformConnector
import support.stubs.{CommonStubs, StubHelper}
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global

class RouterAnalyticsFeature extends WordSpec with MustMatchers with TARIntegrationTest with GivenWhenThen with CommonStubs with StubHelper {

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
    "sendEvents" should {
      "send a GA event to platform-analytics with the correct json " in {
        connector.sendEvents(testData)
        StubAnalyticsPlatformConnector.stubAnalyticsPost(postJson)(200, None)
        verifyAnalytics(
              AnalyticsData("GA1.4.405633776.1470748420", List(
                GaEvent("routing", "business-tax-account", "bta-home-page-for-user-with-no-previous-return", Nil)
              )))
      }
    }
  }
}
