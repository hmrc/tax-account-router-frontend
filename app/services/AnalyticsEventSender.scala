package services

import play.api.mvc.{AnyContent, Request}

trait AnalyticsEventSender {
  def sendRoutingEvent(locationName: String, ruleApplied: String)(implicit request: Request[AnyContent]): Unit = ???
}

object AnalyticsEventSender extends AnalyticsEventSender