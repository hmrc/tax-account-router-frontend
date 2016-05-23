/*
 * Copyright 2016 HM Revenue & Customs
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
import com.kenshoo.play.metrics.MetricsRegistry
import model.{Location, TAuditContext}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

object MetricsMonitoringService extends MetricsMonitoringService {
  override val metricsRegistry: MetricRegistry = MetricsRegistry.defaultRegistry
}

trait MetricsMonitoringService {

  val metricsRegistry: MetricRegistry

  def sendMonitoringEvents(auditContext: TAuditContext, throttledLocation: Location)(implicit authContext: AuthContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Unit] = {

    Future {
      val destinationNameBeforeThrottling = auditContext.getThrottlingDetails.get("destination-name-before-throttling")
      val destinationNameAfterThrottling = throttledLocation.name
      val throttleKey = if (destinationNameBeforeThrottling.isDefined && destinationNameBeforeThrottling.get != destinationNameAfterThrottling) {
        s".throttled-from-${destinationNameBeforeThrottling.get}"
      } else ".not-throttled"

      metricsRegistry.meter(s"routed.to-${throttledLocation.name}.because-${auditContext.ruleApplied}$throttleKey").mark()

      val trueConditions = auditContext.getReasons.filter { case (k, v) => v == "true" }.keys
      trueConditions.foreach(metricsRegistry.meter(_).mark())

      val falseConditions = auditContext.getReasons.filter { case (k, v) => v == "false" }.keys
      falseConditions.foreach(key => metricsRegistry.meter(s"not-$key").mark())
    }
  }

}
