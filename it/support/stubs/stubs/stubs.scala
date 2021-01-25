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
//package support.stubs
//
//import com.github.tomakehurst.wiremock.matching.UrlPattern
//import com.github.tomakehurst.wiremock.client.WireMock._
//import com.github.tomakehurst.wiremock.stubbing.StubMapping
//
//trait Stubs {
//  def createStubs(stub: Stub) {
//    stub.create()
//  }
//}
//
//trait Stub {
//  def create(): Unit
//}
//
//trait StubbedPage {
//  def stubOut(urlMatchingStrategy: UrlPattern, heading: String, extraBodyHtml: Option[String] = None, prodUrl: Option[String] = None): StubMapping = {
//    stubFor(get(urlMatchingStrategy)
//      .willReturn(
//        aResponse()
//          .withStatus(200)
//          .withBody(s"<html><body>${extraBodyHtml.getOrElse("")}<h1>$heading</h1>This is a stub</body></html>")
//      ))
//  }
//}