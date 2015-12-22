package support.page

import com.github.tomakehurst.wiremock.client.WireMock._
import support.Env
import support.stubs.{Stub, StubbedPage}

object PortalHomeStubPage extends Stub with StubbedPage {
  override def create() = {
    stubOut(urlPathEqualTo("/ssoout-non-digital-session"), "Portal Home Page", queryParams = Seq(("continue", matching("/portal"))))
  }
}

object PortalHomePage extends WebPage {
  override val url: String = Env.host + "/ssoout-non-digital-session?continue=/portal"

  override def isCurrentPage: Boolean = find(xpath("//h1")).fold(false)(_.text == "Portal Home Page")
}