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

import com.codahale.metrics.{Meter, MetricRegistry}
import engine.{AuditInfo, ThrottlingInfo}
import model.Location
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor4
import org.scalatest.prop.Tables.Table
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class MetricsMonitoringServiceSpec extends UnitSpec with MockitoSugar with Eventually {

  "MetricsMonitoringService" should {

    val ruleApplied = "rule-applied"

    val scenarios: TableFor4[String, Option[Location], Location, String] = Table(
      ("scenario", "destinationNameBeforeThrottling", "destinationNameAfterThrottling", "expectedMeterName"),
      ("destination not throttled", Some(Location(name = "a", url = "/a")), Location(name = "a", url = "/a"), "routed.to-a.because-rule-applied.not-throttled"),
      ("destination throttled", Some(Location(name = "a", url = "/a")), Location(name = "b", url = "/b"), "routed.to-b.because-rule-applied.throttled-from-a"),
      ("destination before throttling not present", None, Location(name = "b", url = "/b"), "routed.to-b.because-rule-applied.not-throttled")
    )

    forAll(scenarios) { (scenario, destinationNameBeforeThrottling, destinationNameAfterThrottling, expectedMeterName) =>

      s"send monitoring events - scenario: $scenario" in new Setup {

        import engine.RoutingReason._

        // given
        val trueCondition1 = Reason("trueCondition1")
        val trueCondition2 = Reason("trueCondition2")
        val falseCondition1 = Reason("falseCondition1")
        val falseCondition2 = Reason("falseCondition2")

        val routingReasons = Map(
          trueCondition1 -> Some(true),
          trueCondition2 -> Some(true),
          falseCondition1 -> Some(false),
          falseCondition2 -> Some(false)
        )

        val throttlingInfo = ThrottlingInfo(None, true, Location("a", "/a"), true)

        val auditInfo = AuditInfo(routingReasons, Some(ruleApplied), Some(throttlingInfo))

        val throttledLocation = Location(name = "throttled", url = "/throttled")

        val mockRoutedToMeter = mock[Meter]

        when(mockMetricRegistry.meter(eqTo(expectedMeterName))).thenReturn(mockRoutedToMeter)

        val trueCondition1Meter = mock[Meter]
        val trueCondition2Meter = mock[Meter]
        val falseCondition1Meter = mock[Meter]
        val falseCondition2Meter = mock[Meter]
        when(mockMetricRegistry.meter(eqTo(trueCondition1.key))).thenReturn(trueCondition1Meter)
        when(mockMetricRegistry.meter(eqTo(trueCondition2.key))).thenReturn(trueCondition2Meter)
        when(mockMetricRegistry.meter(eqTo(s"not-${falseCondition1.key}"))).thenReturn(falseCondition1Meter)
        when(mockMetricRegistry.meter(eqTo(s"not-${falseCondition2.key}"))).thenReturn(falseCondition2Meter)

        // when
        metricsMonitoringService.sendMonitoringEvents(auditInfo, throttledLocation)

        // then
        eventually {
          verify(trueCondition1Meter).mark()
          verify(trueCondition2Meter).mark()
          verify(falseCondition1Meter).mark()
          verify(falseCondition2Meter).mark()

          verifyNoMoreInteractions(
            mockRoutedToMeter,
            trueCondition1Meter,
            trueCondition2Meter,
            falseCondition1Meter,
            falseCondition2Meter
          )
        }
      }
    }
  }

  trait Setup {
    implicit val fakeRequest = FakeRequest()
    implicit val hc = HeaderCarrier()
    val mockMetricRegistry = mock[MetricRegistry]
    val metricsMonitoringService = new MetricsMonitoringService {
      override val metricsRegistry = mockMetricRegistry
    }

    when(mockMetricRegistry.meter(any[String])).thenReturn(mock[Meter])
  }
}
