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

package connector

import org.scalatest.prop.TableDrivenPropertyChecks._
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.ExecutionContext.Implicits.global

class SelfAssessmentGatewayConnectorSpec extends UnitSpec {

  "Self Assessment Gateway Connector" should {
    "return the stubbed SAUserInfo depending on the userId provided" in {
      val scenarios =
      Table(
        ("scenario", "userId", "partnership", "selfEmployment", "previousReturns"),
        ("User with no previous returns", "Jenifer", false, false, false),
        ("User in partnership", "Morgan", true, false, true),
        ("User with selfEmployment", "Bert", false, true, true),
        ("User not in partnership and without selfEmployment", "VatSaEpayePerformanceTests", false, false, true),
        ("User with previous returns in partnership and selfEmployment", "XXXXXXXXXX", true, true, true)
      )

      forAll(scenarios) { (scenario: String, userId: String, partnership: Boolean, selfEmployment: Boolean, previousReturns: Boolean) =>
        //when
        val saUserInfo: SAUserInfo = await(SelfAssessmentGatewayConnector.getInfo(userId))

        //then
        saUserInfo shouldBe SAUserInfo(partnership, selfEmployment, previousReturns)
      }
    }
  }

}
