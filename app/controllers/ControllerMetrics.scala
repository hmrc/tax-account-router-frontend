/*
 * Copyright 2015 HM Revenue & Customs
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

package controllers

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.MetricsRegistry

trait ControllerMetrics {

  val registry: MetricRegistry

  def registerRedirectFor(name: String) = {
    registry.meter(s"routing-to-$name").mark()
  }
}

object ControllerMetrics extends ControllerMetrics {
  override val registry = MetricsRegistry.defaultRegistry
}
