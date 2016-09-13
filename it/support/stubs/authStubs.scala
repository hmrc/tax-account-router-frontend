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

import java.net.URLEncoder
import java.util.UUID

import auth.RouterAuthenticationProvider
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, PlainText}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, CredentialStrength}
import uk.gov.hmrc.play.http.SessionKeys

object LoggedOutSessionUser extends Stub with StubbedPage {
  override def create = stubOut(
    urlEqualTo("/gg/sign-in?continue=/account"), "Login Page", Some( """<button class="button" type="submit">Sign in</button>"""))
}

trait SessionCookieBaker {
  def cookieValue(sessionData: Map[String, String]) = {
    def encode(data: Map[String, String]): String = {
      val encoded = data.map {
        case (k, v) => URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
      }.mkString("&")
      Crypto.sign(encoded, "yNhI04vHs9<_HWbC`]20u`37=NGLGYY5:0Tg5?y`W<NoJnXWqmjcgZBec@rOxb^G".getBytes) + "-" + encoded
    }

    val encodedCookie = encode(sessionData)
    val encrypted = CompositeSymmetricCrypto.aesGCM("gvBoGdgzqG1AarzF1LY0zQ==", Seq()).encrypt(PlainText(encodedCookie)).value

    s"""mdtp="$encrypted"; Path=/; HTTPOnly"; Path=/; HTTPOnly"""
  }
}

class LoggedInSessionUser(tokenPresent: Boolean,
                          isRegisteredFor2SV: Boolean,
                          accounts: Accounts,
                          credentialStrength: CredentialStrength,
                          affinityGroup: String,
                          oid: String,
                          userDetailsLink: String) extends Stub with SessionCookieBaker {

  private val twoFactorAuthOtpId = if (isRegisteredFor2SV) """"twoFactorAuthOtpId": "1234",""" else ""
  private val credentialStrengthField = s""""credentialStrength": "${credentialStrength.name.toLowerCase}","""
  private val affinityGroupField = s""""affinityGroup": "$affinityGroup","""

  override def create() = {
    val token =
      if (tokenPresent)
        Seq(SessionKeys.token -> "PGdhdGV3YXk6R2F0ZXdheVRva2VuIHhtbG5zOndzdD0iaHR0cDovL3NjaGVtYXMueG1sc29hcC5vcmcvd3MvMjAwNC8wNC90cnVzdCIgeG1sbnM6d3NhPSJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy93cy8yMDA0LzAzL2FkZHJlc3NpbmciIHhtbG5zOndzc2U9Imh0dHA6Ly9kb2NzLm9hc2lzLW9wZW4ub3JnL3dzcy8yMDA0LzAxL29hc2lzLTIwMDQwMS13c3Mtd3NzZWN1cml0eS1zZWNleHQtMS4wLnhzZCIgeG1sbnM6d3N1PSJodHRwOi8vZG9jcy5vYXNpcy1vcGVuLm9yZy93c3MvMjAwNC8wMS9vYXNpcy0yMDA0MDEtd3NzLXdzc2VjdXJpdHktdXRpbGl0eS0xLjAueHNkIiB4bWxuczpzb2FwPSJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy9zb2FwL2VudmVsb3BlLyI")
      else Nil
    val data = Map(
      SessionKeys.sessionId -> s"session-${UUID.randomUUID}",
      SessionKeys.userId -> "/auth/oid/1234567890",
      SessionKeys.authToken -> "PGdhdGV3YXk6R2F0ZXdheVRva2VuIHhtbG5zOndzdD0iaHR0cDovL3NjaGVtYXMueG1sc29hcC5vcmcvd3MvMjAwNC8wNC90cnVzdCIgeG1sbnM6d3NhPSJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy93cy8yMDA0LzAzL2FkZHJlc3NpbmciIHhtbG5zOndzc2U9Imh0dHA6Ly9kb2NzLm9hc2lzLW9wZW4ub3JnL3dzcy8yMDA0LzAxL29hc2lzLTIwMDQwMS13c3Mtd3NzZWN1cml0eS1zZWNleHQtMS4wLnhzZCIgeG1sbnM6d3N1PSJodHRwOi8vZG9jcy5vYXNpcy1vcGVuLm9yZy93c3MvMjAwNC8wMS9vYXNpcy0yMDA0MDEtd3NzLXdzc2VjdXJpdHktdXRpbGl0eS0xLjAueHNkIiB4bWxuczpzb2FwPSJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy9zb2FwL2VudmVsb3BlLyI",
      SessionKeys.name -> "JOHN THE SAINSBURY",
      SessionKeys.affinityGroup -> affinityGroup,
      SessionKeys.authProvider -> RouterAuthenticationProvider.id
    ) ++ token


    stubFor(get(urlEqualTo("/gg/sign-in?continue=/account"))
      .willReturn(aResponse()
        .withStatus(303)
        .withHeader(HeaderNames.SET_COOKIE, cookieValue(data))
        .withHeader(HeaderNames.SET_COOKIE, s"""_ga="GA1.4.405633776.1470748420"; Path=/; HTTPOnly"; Path=/; HTTPOnly""")
        .withHeader(HeaderNames.LOCATION, "http://localhost:9000/account")))


    stubFor(post(urlEqualTo("/login"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""
               |{
               |    s"authId": "/auth/oid/$oid",
               |    "credId": "cred-id-12345",
               |    "name": "JOHN THE SAINSBURY",
               |    $affinityGroupField
               |    "encodedGovernmentGatewayToken": "PGdhdGV3YXk6R2F0ZXdheVRva2VuIHhtbG5zOndzdD0iaHR0cDovL3NjaGVtYXMueG1sc29hcC5vcmcvd3MvMjAwNC8wNC90cnVzdCIgeG1sbnM6d3NhPSJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy93cy8yMDA0LzAzL2FkZHJlc3NpbmciIHhtbG5zOndzc2U9Imh0dHA6Ly9kb2NzLm9hc2lzLW9wZW4ub3JnL3dzcy8yMDA0LzAxL29hc2lzLTIwMDQwMS13c3Mtd3NzZWN1cml0eS1zZWNleHQtMS4wLnhzZCIgeG1sbnM6d3N1PSJodHRwOi8vZG9jcy5vYXNpcy1vcGVuLm9yZy93c3MvMjAwNC8wMS9vYXNpcy0yMDA0MDEtd3NzLXdzc2VjdXJpdHktdXRpbGl0eS0xLjAueHNkIiB4bWxuczpzb2FwPSJodHRwOi8vc2NoZW1hcy54bWxzb2FwLm9yZy9zb2FwL2VudmVsb3BlLyI+PGdhdGV3YXk6Q3JlYXRlZD4yMDE0LTA2LTA5VDA5OjM5OjA2WjwvZ2F0ZXdheTpDcmVhdGVkPjxnYXRld2F5OkV4cGlyZXM+MjAxNC0wNi0wOVQxMzozOTowNlo8L2dhdGV3YXk6RXhwaXJlcz48Z2F0ZXdheTpVc2FnZT5TdGFuZGFyZDwvZ2F0ZXdheTpVc2FnZT48Z2F0ZXdheTpPcGFxdWU+ZXlKamNtVmtTV1FpT2lKamNtVmtMV2xrTFRVME16SXhNak13TURBeE9TSXNJbU55WldGMGFXOXVWR2x0WlNJNklqSXdNVFF0TURZdE1EbFVNRGs2TXprNk1EWXVNREF3V2lJc0ltVjRjR2x5ZVZScGJXVWlPaUl5TURFMExUQTJMVEE1VkRFek9qTTVPakEyTGpBd01Gb2lmUT09PC9nYXRld2F5Ok9wYXF1ZT48L2dhdGV3YXk6R2F0ZXdheVRva2VuPg=="
               |}|
            """.stripMargin
          )))

    stubFor(get(urlEqualTo("/auth/authority"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""
               |{
               |    $twoFactorAuthOtpId
               |    "uri": "/auth/oid/1234567890",
               |    "loggedInAt": "2014-06-09T14:57:09.522Z",
               |    "accounts":${Json.toJson(accounts)},
               |    "levelOfAssurance": "2",
               |    $credentialStrengthField
               |    "confidenceLevel": 500,
               |    "enrolments": "/enrolments-uri",
               |    "userDetailsLink": "$userDetailsLink"
               |}
               |"""
              .stripMargin
          )))
  }
}

object LoggedInSessionUser {
  def apply(tokenPresent: Boolean,
            isRegisteredFor2SV: Boolean,
            accounts: Accounts,
            credentialStrength: CredentialStrength,
            affinityGroup: String,
            oid: String,
            userDetailsLink: String) =
    new LoggedInSessionUser(tokenPresent, isRegisteredFor2SV, accounts, credentialStrength, affinityGroup, oid, userDetailsLink)
}
