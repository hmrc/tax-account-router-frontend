package services

import connector.{AnalyticsData, AnalyticsPlatformConnector, GaEvent}
import play.api.Logger
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.http.HeaderCarrier

trait AnalyticsEventSender {

  private val routingCategory = "routing"

  def analyticsPlatformConnector: AnalyticsPlatformConnector

  private def gaClientId(request: Request[Any]) = request.cookies.get("_ga").map(_.value)

  def sendRoutingEvent(locationName: String, ruleApplied: String)(implicit request: Request[AnyContent], hc: HeaderCarrier) = {
    gaClientId(request).fold(Logger.warn(s"Couldn't get _ga cookie from request $request")) {
      clientId => analyticsPlatformConnector.sendEvents(AnalyticsData(clientId, List(GaEvent(routingCategory, locationName, ruleApplied))))
    }
  }
}

object AnalyticsEventSender extends AnalyticsEventSender {
  override lazy val analyticsPlatformConnector = AnalyticsPlatformConnector
}