package services

import connector.{AnalyticsData, AnalyticsPlatformConnector, GaEvent}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.mvc.Cookie
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec

class AnalyticsEventSenderSpec extends UnitSpec with MockitoSugar {

  "AnalyticsEventSender" should {

    "send a routing event to GA" in new Setup {
      val locationName = "some-location"
      val ruleApplied = "some-rule"

      analyticsEventSender.sendRoutingEvent(locationName, ruleApplied)

      verify(mockAnalyticsPlatformConnector).sendEvents(AnalyticsData(gaClientId, List(GaEvent("routing", locationName, ruleApplied))))
    }
  }

  sealed trait Setup {
    val gaClientId = "gaClientId"
    implicit val request = FakeRequest().withCookies(Cookie("_ga", gaClientId))
    val mockAnalyticsPlatformConnector = mock[AnalyticsPlatformConnector]
    val analyticsEventSender = new AnalyticsEventSender {
      override val analyticsPlatformConnector = mockAnalyticsPlatformConnector
    }
  }

}
