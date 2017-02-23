/*
 * Copyright 2017 HM Revenue & Customs
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

package services

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import engine.AuditInfo
import model.Location
import play.api.Play
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

object MetricsMonitoringService extends MetricsMonitoringService {
  override val metricsRegistry = Play.current.injector.instanceOf[Metrics].defaultRegistry
}

trait MetricsMonitoringService {

  val metricsRegistry: MetricRegistry

  def sendMonitoringEvents(auditInfo: AuditInfo, throttledLocation: Location)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Unit] = {

    Future {
      val destinationNameBeforeThrottling: Option[String] = auditInfo.throttlingInfo.map(_.initialDestination.name)
      val destinationNameAfterThrottling: String = throttledLocation.name
      val throttleKey: String = if (destinationNameBeforeThrottling.isDefined && destinationNameBeforeThrottling.get != destinationNameAfterThrottling) {
        s".throttled-from-${destinationNameBeforeThrottling.get}"
      } else ".not-throttled"

      metricsRegistry.meter(s"routed.to-${throttledLocation.name}.because-${auditInfo.ruleApplied}$throttleKey").mark()

      val trueConditions = auditInfo.routingReasons.filter { case (_, Some(v)) => v }.keys
      trueConditions.foreach(reason => metricsRegistry.meter(reason.key).mark())

      val falseConditions = auditInfo.routingReasons.filter { case (_, Some(v)) => !v }.keys
      falseConditions.foreach(reason => metricsRegistry.meter(s"not-${reason.key}").mark())
    }
  }

}
