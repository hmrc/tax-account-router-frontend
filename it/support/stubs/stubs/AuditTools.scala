//package support.stubs
//
//import com.github.tomakehurst.wiremock.client.WireMock
//import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
//import engine.AuditInfo
//import org.scalatest.Matchers
//import play.api.libs.json.{JsObject, JsValue, Json}
//
//import scala.collection.JavaConverters._
//
//trait AuditTools { self: Matchers =>
//
//  case class ThrottlingDetails(enabled: Boolean,
//                               percentage: String,
//                               throttled: Boolean,
//                               destinationUrlBeforeThrottling: String,
//                               destinationNameBeforeThrottling: String)
//
//  val emptyRoutingReasons: Map[String, String] = AuditInfo.emptyReasons.map { case (k, _) => k.key -> "-" }
//
//  def toJson(map: Map[String, String]): JsObject = Json.obj(map.map { case (k, v) => k -> Json.toJsFieldJsValueWrapper(v) }.toSeq: _*)
//
//  def verifyAuditEvent(auditEventStub: RequestPatternBuilder,
//                       expectedReasons: JsValue,
//                       expectedTransactionName: String,
//                       ruleApplied: String,
//                       throttlingDetails: ThrottlingDetails): Unit = {
//
//    val loggedRequests = WireMock.findAll(auditEventStub).asScala.toList
//    val event = Json.parse(loggedRequests
//      .filter(s => s.getBodyAsString.matches( """^.*"auditType"[\s]*\:[\s]*"Routing".*$""")).head.getBodyAsString)
//    (event \ "tags" \ "transactionName").as[String] shouldBe expectedTransactionName
//    (event \ "detail" \ "ruleApplied").as[String] shouldBe ruleApplied
//    (event \ "detail" \ "reasons").get shouldBe expectedReasons
//    (event \ "detail" \ "throttling" \ "enabled").as[String] shouldBe throttlingDetails.enabled.toString
//    (event \ "detail" \ "throttling" \ "percentage").as[String] shouldBe throttlingDetails.percentage
//    (event \ "detail" \ "throttling" \ "throttled").as[String] shouldBe throttlingDetails.throttled.toString
//    (event \ "detail" \ "throttling" \ "destination-url-before-throttling").as[String] should endWith(throttlingDetails.destinationUrlBeforeThrottling)
//    (event \ "detail" \ "throttling" \ "destination-name-before-throttling").as[String] shouldBe throttlingDetails.destinationNameBeforeThrottling
//  }
//}
