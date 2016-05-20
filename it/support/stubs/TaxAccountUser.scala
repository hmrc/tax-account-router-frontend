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

import connector.AffinityGroupValue
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, CredentialStrength}

class TaxAccountUser(loggedIn: Boolean,
                     tokenPresent: Boolean,
                     accounts: Accounts,
                     credentialStrength: CredentialStrength,
                     affinityGroup: String)
  extends Stub {

  def create() = {
    if (loggedIn) {
      LoggedInSessionUser(tokenPresent, accounts, credentialStrength, affinityGroup).create()

    } else {
      LoggedOutSessionUser.create
    }
  }
}

object TaxAccountUser {

  def apply(loggedIn: Boolean = true,
            tokenPresent: Boolean = true,
            accounts: Accounts = Accounts(),
            credentialStrength: CredentialStrength = CredentialStrength.None,
            affinityGroup: String = AffinityGroupValue.ORGANISATION) = {
    new TaxAccountUser(loggedIn, tokenPresent, accounts, credentialStrength, affinityGroup)
  }
}
