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

package support.page

import org.openqa.selenium.support.ui.{ExpectedCondition, WebDriverWait}
import org.openqa.selenium.{By, WebDriver, WebElement}
import org.scalatest.ShouldMatchers
import org.scalatest.selenium.{Page, WebBrowser}
import support.sugar.ImplicitWebDriverSugar

trait WebPage extends Page with WebBrowser with ShouldMatchers with ImplicitWebDriverSugar {

  def assertPageLoaded(): Unit

  def heading = tagName("h1").element.text

  def bodyText = tagName("body").element.text

  def at() = {
    loadPage()
    assertPageLoaded()
  }

  def assertPageIs(expectedPage: String) {
    assertResult(expectedPage, "The actual page is: " + webDriver.getPageSource)(heading)
  }

  def containsFragment(fragment: String) =
    webDriver.getPageSource.contains(fragment)

  def clickElement(elementId: String) = click on id(elementId)


  private def loadPage()(implicit webDriver: WebDriver) = {
    val wait = new WebDriverWait(webDriver, 30)
    wait.until(
      new ExpectedCondition[WebElement] {
        override def apply(d: WebDriver) = d.findElement(By.tagName("body"))
      }
    )
  }
}
