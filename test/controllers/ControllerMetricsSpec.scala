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

import com.codahale.metrics.{Meter, MetricRegistry}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

class ControllerMetricsSpec extends UnitSpec with MockitoSugar {

  "controller metrics" should {

    "should mark an event using a properly named registry" in {

      val eventName = "some-event"

      val mockMetricRegistry = mock[MetricRegistry]
      val mockMeter = mock[Meter]

      when(mockMetricRegistry.meter("routing-to-some-event")).thenReturn(mockMeter)

      val controllerMetrics = new ControllerMetrics {
        override val registry: MetricRegistry = mockMetricRegistry
      }

      controllerMetrics.registerRedirectFor(eventName)

      verify(mockMetricRegistry).meter("routing-to-some-event")
      verify(mockMeter).mark()
    }
  }
}
