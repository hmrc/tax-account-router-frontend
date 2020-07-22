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

import java.net.URL

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import configuration.StubApplicationConfiguration
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxProfile}
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}
import support.stubs.StubbedFeatureSpec

import scala.util.Properties

object Env {

  val host: String = s"http://localhost:${StubbedFeatureSpec.fakeApplicationPort}"

  val stubPort: Int = StubApplicationConfiguration.wiremockPort
  val stubHost: String = "localhost"
  val wireMockServer: WireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  lazy val firefoxDriver: FirefoxDriver = {
    val profile: FirefoxProfile = new FirefoxProfile
    profile.setPreference("javascript.enabled", true)
    profile.setAcceptUntrustedCertificates(true)
    new FirefoxDriver(profile)
  }

  def createBrowser(): ChromeDriver = {
    val capabilities = DesiredCapabilities.chrome()
    new ChromeDriver(capabilities)
  }

  def getInstance(): ChromeDriver = {
    val instance = createBrowser()
    instance
  }

  lazy val chromeWebDriver: ChromeDriver = {
    System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver")
    System.setProperty("browser", "chrome")
    getInstance()
  }

  def createRemoteChrome: WebDriver = {
    new RemoteWebDriver(new URL(s"http://localhost:4444/wd/hub"), DesiredCapabilities.chrome())
  }

  def createRemoteFirefox: WebDriver = {
    new RemoteWebDriver(new URL(s"http://localhost:4444/wd/hub"), DesiredCapabilities.firefox())
  }

  private val browser: String = Properties.propOrElse("browser", "chrome")

  val webDriver: WebDriver = browser match {
    case "firefox"        => firefoxDriver
    case "chrome"         => chromeWebDriver
    case "remote-chrome"  => createRemoteChrome
    case "remote-firefox" => createRemoteFirefox
  }

  val driver: WebDriver = webDriver
}
