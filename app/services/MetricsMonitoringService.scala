/*
 * Copyright 2021 HM Revenue & Customs
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
import javax.inject.{Inject, Singleton}
import model.Location

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MetricsMonitoringServiceImpl @Inject()(metrics: Metrics) extends MetricsMonitoringService {
  override lazy val metricsRegistry: MetricRegistry = metrics.defaultRegistry
}

trait MetricsMonitoringService {

  val metricsRegistry: MetricRegistry

  def sendMonitoringEvents(auditInfo: AuditInfo, throttledLocation: Location)
                          (implicit ec: ExecutionContext): Future[Unit] = {

    Future {
      val destinationNameBeforeThrottling = auditInfo.throttlingInfo.map(_.initialDestination.name)

      val throttleKey = destinationNameBeforeThrottling match {
        case Some(name) if name != throttledLocation.name => s".throttled-from-$name"
        case _ => ".not-throttled"
      }

      auditInfo.ruleApplied.foreach { ruleApplied =>
        metricsRegistry.meter(s"routed.to-${throttledLocation.name}.because-$ruleApplied$throttleKey").mark()
      }

      val trueConditions = auditInfo.routingReasons.filter { case (_, Some(v)) => v }.keys
      trueConditions.foreach(reason => metricsRegistry.meter(reason.key).mark())

      val falseConditions = auditInfo.routingReasons.filter { case (_, Some(v)) => !v }.keys
      falseConditions.foreach(reason => metricsRegistry.meter(s"not-${reason.key}").mark())
    }
  }

}
