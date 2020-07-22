package support.page

import com.github.tomakehurst.wiremock.client.WireMock._
import support.Env
import support.stubs.{Stub, StubbedPage}

object BtaHomeStubPage extends Stub with StubbedPage {
  override def create(): Unit = {
    stubOut(urlMatching("/business-account"), "BTA Home Page")
  }
}


object BtaHomePage extends WebPage {
  override val url: String = Env.host

  override def assertPageLoaded(): Unit = assertPageIs("BTA Home Page")
}
