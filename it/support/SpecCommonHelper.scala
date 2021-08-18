package support

import config.FrontendAppConfig
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSRequest}
import support.WireMockConstants.{stubHost, stubPort}

trait SpecCommonHelper extends PlaySpec with GuiceOneServerPerSuite with WireMockMocks with BeforeAndAfterAll with BeforeAndAfterEach {

  lazy val wireMock = new WireMock

  def buildClient(path: String): WSRequest = {
    app.injector.instanceOf[WSClient].url(s"http://localhost:$port/$path")
  }

  val extraConfig: Map[String, Any] = {
    Map[String, Any](
      "metrics.enabled" -> false,
      "play.http.router" -> "testOnlyDoNotUseInAppConf.Routes",
      "auditing.consumer.baseUri.host" -> stubHost,
      "auditing.consumer.baseUri.port" -> stubPort,
      "microservice.services.auth.host" -> stubHost,
      "microservice.services.auth.port" -> stubPort,
      "enrolment-store.host" -> s"http://$stubHost:$stubPort",
      "new-rules" -> true
    )
  }

  override lazy val app = new GuiceApplicationBuilder()
    .configure(extraConfig)
    .build()

  lazy val appConfig = app.injector.instanceOf[FrontendAppConfig]

  override protected def beforeAll(): Unit = {
    wireMock.start()
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    wireMock.resetAll()
    standardDataStreamAuditMock()
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    wireMock.stop()
    super.afterAll()
  }
}