package support.stubs.stubs

import support.stubs.StubHelper

object StubAnalyticsPlatformConnector extends StubHelper {

  def stubAnalyticsPost(postBody: String)(status: Int, optBody: Option[String]): Unit =
    stubPost("/platform-analytics/event", status, postBody, optBody)

}
