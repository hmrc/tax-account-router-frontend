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
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor3
import org.scalatest.prop.Tables.Table
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class MetricsMonitoringServiceSpec extends UnitSpec with MockitoSugar with Eventually {

  "MetricsMonitoringService" should {

    val scenarios: TableFor3[String, Option[Location], Location] = Table(
      ("scenario", "destinationNameBeforeThrottling", "destinationNameAfterThrottling"),
      ("destination not throttled", Some(Location(name = "a", url = "/a")), Location(name = "a", url = "/a")),
      ("destination throttled", Some(Location(name = "a", url = "/a")), Location(name = "b", url = "/b")),
      ("destination before throttling not present", None, Location(name = "b", url = "/b"))
    )

    forAll(scenarios) { (scenario, destinationNameBeforeThrottling, destinationNameAfterThrottling) =>

      s"send monitoring events - scenario: $scenario" in new Setup {

        import engine.RoutingReason._

        // given
        val c1 = IS_A_VERIFY_USER
        val c2 = IS_A_GOVERNMENT_GATEWAY_USER
        val c3 = IS_IN_A_PARTNERSHIP
        val c4 = IS_SELF_EMPLOYED

        val routingReasons = Map(
          c1 -> Some(true),
          c2 -> Some(true),
          c3 -> Some(false),
          c4 -> Some(false)
        )

        val ruleApplied = "rule-applied"

        val throttlingInfo = ThrottlingInfo(None, true, Location("a", "/a"), true)

        val auditInfo = AuditInfo(routingReasons, Some(ruleApplied), Some(throttlingInfo))

        val throttledLocation = Location(name = "throttled", url = "/throttled")

        val mockRoutedToMeter = mock[Meter]

        if (destinationNameBeforeThrottling.isDefined && destinationNameBeforeThrottling.get != destinationNameAfterThrottling) {
          when(mockMetricRegistry.meter(eqTo(s"routed.to-$destinationNameAfterThrottling.because-$ruleApplied.throttled-from-${destinationNameBeforeThrottling.get}"))).thenReturn(mockRoutedToMeter)
        } else {
          when(mockMetricRegistry.meter(eqTo(s"routed.to-$destinationNameAfterThrottling.because-$ruleApplied.not-throttled"))).thenReturn(mockRoutedToMeter)
        }

        val c1Meter = mock[Meter]
        val c2Meter = mock[Meter]
        val c3Meter = mock[Meter]
        val c4Meter = mock[Meter]
        when(mockMetricRegistry.meter(eqTo(c1.key))).thenReturn(c1Meter)
        when(mockMetricRegistry.meter(eqTo(c2.key))).thenReturn(c2Meter)
        when(mockMetricRegistry.meter(eqTo(s"not-${c3.key}"))).thenReturn(c3Meter)
        when(mockMetricRegistry.meter(eqTo(s"not-${c4.key}"))).thenReturn(c4Meter)

        // when
        metricsMonitoringService.sendMonitoringEvents(auditInfo, throttledLocation)

        // then
        eventually {

          verify(c1Meter).mark()
          verify(c2Meter).mark()
          verify(c3Meter).mark()
          verify(c4Meter).mark()

          verifyNoMoreInteractions(
            mockRoutedToMeter,
            c1Meter,
            c2Meter,
            c3Meter,
            c4Meter
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
