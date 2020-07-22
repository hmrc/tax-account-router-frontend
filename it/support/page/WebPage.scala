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
import org.scalatest.Matchers
import org.scalatest.selenium.{Page, WebBrowser}
import support.sugar.ImplicitWebDriverSugar

trait WebPage extends Page with WebBrowser with Matchers with ImplicitWebDriverSugar {

  def assertPageLoaded(): Unit

  def heading: String = tagName("h1").element.text

  def bodyText: String = tagName("body").element.text

  def at(): Unit = {
    loadPage()
    assertPageLoaded()
  }

  def assertPageIs(expectedPage: String): Unit = {
    val title =  find(xpath("//h1")).map(_.text)
    assertResult(Some(expectedPage), s"The actual page url is [${webDriver.getCurrentUrl}] and the content is: [${webDriver.getPageSource}]")(title)
  }

  def containsFragment(fragment: String): Boolean =
    webDriver.getPageSource.contains(fragment)

  def clickElement(elementId: String): Unit = click on id(elementId)


  private def loadPage()(implicit webDriver: WebDriver): WebElement = {
    val wait = new WebDriverWait(webDriver, 30)
    wait.until(
      new ExpectedCondition[WebElement] {
        override def apply(d: WebDriver): WebElement = d.findElement(By.tagName("body"))
      }
    )
  }
}
