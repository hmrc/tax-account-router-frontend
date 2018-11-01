package support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import connector.AnalyticsData
import play.api.libs.json.Json

object PlatformAnalyticsStub {

  def verifyAnalytics(analyticsData: AnalyticsData): Unit = {
    verify(postRequestedFor(urlEqualTo("/platform-analytics/event"))
      .withRequestBody(equalToJson(Json.toJson(analyticsData).toString())))
  }

}
