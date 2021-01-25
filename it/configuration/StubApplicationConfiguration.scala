///*
// * Copyright 2015 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package configuration
//
//object StubApplicationConfiguration {
//  val wiremockPort: Int = 6009
//}
//trait StubApplicationConfiguration {
//
//  val stubPort: Int = StubApplicationConfiguration.wiremockPort
//  val stubHost = "localhost"
//
//  val databaseName = "tar-test"
//
//  val stubbedMicroServices: Map[String, Any] = Seq("auth", "sa", "user-details", "platform-analytics")
//    .map(service => Map(
//      s"microservice.services.$service.host" -> stubHost,
//      s"microservice.services.$service.port" -> stubPort
//    )).reduce(_ ++ _)
//
//  val config: Map[String, Any] = Map[String, Any](
//
//
//
//  ) ++ stubbedMicroServices
//}