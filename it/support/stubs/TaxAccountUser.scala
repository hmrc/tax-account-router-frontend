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

package support.stubs

import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts

class TaxAccountUser(loggedIn: Boolean,
                     firstTimeLoggedIn: Boolean,
                     tokenPresent: Boolean,
                     accounts: Accounts)
  extends Stub {

  def create() = {
    if (loggedIn) {
      LoggedInSessionUser(firstTimeLoggedIn, tokenPresent, accounts).create

    } else {
      LoggedOutSessionUser.create
    }
  }
}

object TaxAccountUser {

  def apply(loggedIn: Boolean = true,
            firstTimeLoggedIn: Boolean = false,
            tokenPresent: Boolean = true,
            accounts: Accounts = Accounts()) = {
    new TaxAccountUser(loggedIn, firstTimeLoggedIn, tokenPresent, accounts)
  }
}
