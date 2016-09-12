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

package support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxProfile}
import org.openqa.selenium.phantomjs.PhantomJSDriver
import org.openqa.selenium.phantomjs.PhantomJSDriverService.PHANTOMJS_PAGE_SETTINGS_PREFIX
import org.openqa.selenium.remote.DesiredCapabilities

object Env {

  var host = "http://localhost:9000"

  val stubPort = 11111
  val stubHost = "localhost"
  val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  lazy val firefoxDriver = {
    val profile: FirefoxProfile = new FirefoxProfile
    profile.setPreference("javascript.enabled", true)
    profile.setAcceptUntrustedCertificates(true)
    new FirefoxDriver(profile)
  }

  lazy val phantomJsDriver = {
    val capabilities = DesiredCapabilities.phantomjs()
    capabilities.setCapability(PHANTOMJS_PAGE_SETTINGS_PREFIX + "resourceTimeout", 10)
    new PhantomJSDriver(capabilities)
  }

  def createBrowser() = {
    val capabilities = DesiredCapabilities.chrome()
    new ChromeDriver(capabilities)
  }

  def getInstance() = {
    val instance = createBrowser()
    instance.manage().window().maximize()
    instance
  }

  lazy val chromeWebDriver = {
    val os = System.getProperty("os.name").toLowerCase.replaceAll(" ", "")
    val chromeDriver = getClass.getResource("/chromedriver/chromedriver_" + os).getPath
    Runtime.getRuntime.exec("chmod u+x " + chromeDriver)
    System.setProperty("webdriver.chrome.driver", chromeDriver)
    System.setProperty("browser", "chrome")
    getInstance()
  }

  val driver = chromeWebDriver

}
