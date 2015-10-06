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

import scala.concurrent.{ExecutionContext, Future}


trait SelfAssessmentGatewayConnector {

  def getInfo(userId: String)(implicit ec: ExecutionContext) : Future[SAUserInfo]

}

object SelfAssessmentGatewayConnector extends SelfAssessmentGatewayConnector {


  def getInfo(userId: String)(implicit ec: ExecutionContext) : Future[SAUserInfo] = {
    val saUserInfo = userId match {
      case "Jenifer" => SAUserInfo(partnership = false, selfEmployment = false, previousReturns = false)
      case "Morgan" => SAUserInfo(partnership = true, selfEmployment = false, previousReturns = true)
      case "Bert" => SAUserInfo(partnership = false, selfEmployment = true, previousReturns = true)
      case "VatSaEpayePerformanceTests" => SAUserInfo(partnership = false, selfEmployment = false, previousReturns = true)
      case _ => SAUserInfo(partnership = true, selfEmployment = true, previousReturns = true)
    }
    Future(saUserInfo)
  }

}

case class SAUserInfo(partnership: Boolean = false, selfEmployment: Boolean = false, previousReturns: Boolean = false)
