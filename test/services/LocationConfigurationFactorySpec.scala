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

import config.FrontendAppConfig
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import support.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class LocationConfigurationFactorySpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

//  "configurationForLocation" should {
//    "retrieve appropriate configuration" in new Setup {
//      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
//
//      when(mockAppConfig.getThrottlingConfig("name1")).thenReturn(testThrottlingConfig)
//
//      val location: Location = Location("name1", "some-url")
//
//      val result: ThrottlingConfig = throttlingService.configurationForLocation(location, fakeRequest)
//
//      result shouldBe testThrottlingConfig
//    }
//
//    "retrieve appropriate configuration for PTA without token in session" in new Setup {
//      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
//
//      when(mockAppConfig.getThrottlingConfig("personal-tax-account-verify")).thenReturn(testThrottlingConfig)
//
//      val location: Location = Locations.PersonalTaxAccount
//
//      val result: ThrottlingConfig = throttlingService.configurationForLocation(location, fakeRequest)
//
//      result shouldBe testThrottlingConfig
//    }
//
//    "retrieve appropriate configuration for PTA with token in session" in new Setup {
//
//      implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession("token" -> "some-token")
//
//      when(mockAppConfig.getThrottlingConfig("personal-tax-account-gg")).thenReturn(testThrottlingConfig)
//
//      val location: Location = Locations.PersonalTaxAccount
//
//      val result: ThrottlingConfig = throttlingService.configurationForLocation(location, fakeRequest)
//
//      result shouldBe testThrottlingConfig
//    }
 // }

  class Setup {
   val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]

   val testThrottlingConfig: ThrottlingConfig = ThrottlingConfig(100, Some("some-fallback"))

//   // val locationConfigurationFactory: LocationConfigurationFactory = new LocationConfigurationFactory {
//      override val configuration: AppConfig = mockAppConfig
//    }
    val throttlingService: ThrottlingService = new ThrottlingService(mockAppConfig)
  }
}
