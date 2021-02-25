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

package model

import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class RoutingInfo(routedDestination: String, throttledDestination: String, expirationTime: DateTime)

object RoutingInfo {

  implicit val dateTimeRead = ReactiveMongoFormats.dateTimeRead
  implicit val dateTimeWrite = ReactiveMongoFormats.dateTimeWrite
  implicit val routingInfoWrites: Writes[RoutingInfo] = Json.writes[RoutingInfo]
  implicit val routingInfoReads: Reads[RoutingInfo] = (
    (JsPath \ "routingInfo" \ "routedDestination").read[String] and
    (JsPath \ "routingInfo" \ "throttledDestination").read[String] and
    (JsPath \ "routingInfo" \ "expirationTime").read[DateTime]
    )(RoutingInfo.apply _)

}
