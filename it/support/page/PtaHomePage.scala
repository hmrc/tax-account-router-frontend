package support.page

import com.github.tomakehurst.wiremock.client.WireMock._
import support.Env
import support.stubs.{Stub, StubbedPage}

object PtaHomeStubPage extends Stub with StubbedPage {
  override def create() = {
    stubOut(urlMatching("/personal-account"), "PTA Home Page")
  }
}

object PtaHomePage extends WebPage {
  override val url: String = Env.host + "/personal-account"

  override def assertPageLoaded() = assertPageIs("PTA Home Page")
}
