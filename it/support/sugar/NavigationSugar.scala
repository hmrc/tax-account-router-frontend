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

package support.sugar

import org.openqa.selenium.{By, WebDriver}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.selenium.WebBrowser
import org.scalatest.selenium.WebBrowser.{go => goo}
import support.page.WebPage


trait NavigationSugar extends WebBrowser with Eventually with IntegrationPatience {

  def go(page: WebPage)(implicit webDriver: WebDriver): Unit = {
    goo to page
  }

  def on(page: WebPage)(implicit webDriver: WebDriver): Unit = {
    eventually {
      webDriver.findElement(By.tagName("body"))
      page.assertPageLoaded()
    }
  }

}
