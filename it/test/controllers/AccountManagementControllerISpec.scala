/*
 * Copyright 2026 HM Revenue & Customs
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

package controllers

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlEqualTo}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import support.SpecCommonHelper
import uk.gov.hmrc.http.SessionKeys

import scala.concurrent.Future

class AccountManagementControllerISpec extends SpecCommonHelper {

  val testRedirectController: AccountManagementController = app.injector.instanceOf[AccountManagementController]

  private def authedRequest(path: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest("GET", path).withSession(
      SessionKeys.authToken -> "Bearer test-token",
      SessionKeys.sessionId -> "session-test-id"
    )

  val profileRequest: FakeRequest[AnyContentAsEmpty.type] = authedRequest("/profile-management")
  val groupProfileRequest: FakeRequest[AnyContentAsEmpty.type] = authedRequest("/profile-management-group")

  val profileUrl = "http://localhost:9999/profile"
  val groupProfileUrl = "http://localhost:9999/group-profile"

  val nonAgentRedirect = "https://local.tax.service.gov.uk/account"
  val agentRedirect = "https://local.tax.service.gov.uk/account/portal"

  def authResponse(affinity: String = "Individual",
                   profile: Option[String] = Some(profileUrl),
                   groupProfile: Option[String] = Some(groupProfileUrl)): JsValue = {
    val profileJson = profile.map(p => s""""profile": "$p",""").getOrElse("")
    val groupProfileJson = groupProfile.map(g => s""""groupProfile": "$g",""").getOrElse("")
    Json.parse(
      s"""
         |{
         |  $profileJson
         |  $groupProfileJson
         |  "affinityGroup": "$affinity"
         |}
         |""".stripMargin)
  }

  def stubUnauthorised(reason: String = "MissingBearerToken"): Unit =
    stubFor(post(urlEqualTo("/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(UNAUTHORIZED)
        .withHeader("WWW-Authenticate", s"""MDTP detail="$reason"""")))

  def expectedLocation(base: String, redirectUri: String): String =
    s"$base?redirect_uri=${java.net.URLEncoder.encode(redirectUri, "UTF-8")}"

  "redirectToProfileManagement" must {
    "redirect to the profile url with the non-agent redirect_uri" when {
      "the user has Individual affinity" in {
        stubAuthorised(authResponse(affinity = "Individual").toString)
        val route: Future[Result] = testRedirectController.redirectToProfileManagement(None)(profileRequest)

        status(route) mustBe FOUND
        redirectLocation(route).get mustBe expectedLocation(profileUrl, nonAgentRedirect)
      }

      "the user has Organisation affinity" in {
        stubAuthorised(authResponse(affinity = "Organisation").toString)
        val route: Future[Result] = testRedirectController.redirectToProfileManagement(None)(profileRequest)

        status(route) mustBe FOUND
        redirectLocation(route).get mustBe expectedLocation(profileUrl, nonAgentRedirect)
      }

      "a redirect_uri is supplied that is not on the allowlist" in {
        stubAuthorised(authResponse(affinity = "Individual").toString)
        val route: Future[Result] = testRedirectController.redirectToProfileManagement(Some("https://nonallowlisted.com/test"))(profileRequest)

        status(route) mustBe FOUND
        redirectLocation(route).get mustBe expectedLocation(profileUrl, nonAgentRedirect)
      }
    }

    "redirect to the profile url with the agent redirect_uri" when {
      "the user has Agent affinity" in {
        stubAuthorised(authResponse(affinity = "Agent").toString)
        val route: Future[Result] = testRedirectController.redirectToProfileManagement(None)(profileRequest)

        status(route) mustBe FOUND
        redirectLocation(route).get mustBe expectedLocation(profileUrl, agentRedirect)
      }

      "the user has Agent affinity and a redirect_uri not on the allowlist" in {
        stubAuthorised(authResponse(affinity = "Agent").toString)
        val route: Future[Result] = testRedirectController.redirectToProfileManagement(Some("https://nonallowlisted.com/test"))(profileRequest)

        status(route) mustBe FOUND
        redirectLocation(route).get mustBe expectedLocation(profileUrl, agentRedirect)
      }
    }

    "redirect to sign in" when {
      "auth returns no profile" in {
        stubAuthorised(authResponse(profile = None).toString)
        val route: Future[Result] = testRedirectController.redirectToProfileManagement(None)(profileRequest)

        status(route) mustBe SEE_OTHER
        redirectLocation(route).get must include("/bas-gateway/sign-in")
        redirectLocation(route).get must include("continue_url=%2Fprofile-management")
      }

      Seq("MissingBearerToken", "BearerTokenExpired", "InvalidBearerToken", "SessionRecordNotFound").foreach { reason =>
        s"auth returns $reason" in {
          stubUnauthorised(reason)
          val route: Future[Result] = testRedirectController.redirectToProfileManagement(None)(profileRequest)

          status(route) mustBe SEE_OTHER
          redirectLocation(route).get must include("/bas-gateway/sign-in")
          redirectLocation(route).get must include("continue_url=%2Fprofile-management")
        }
      }
    }
  }

  "redirectToGroupProfileManagement" must {
    "redirect to the group profile url with the non-agent redirect_uri" when {
      "the user has Individual affinity" in {
        stubAuthorised(authResponse(affinity = "Individual").toString)
        val route: Future[Result] = testRedirectController.redirectToGroupProfileManagement(None)(groupProfileRequest)

        status(route) mustBe FOUND
        redirectLocation(route).get mustBe expectedLocation(groupProfileUrl, nonAgentRedirect)
      }

      "the user has Organisation affinity" in {
        stubAuthorised(authResponse(affinity = "Organisation").toString)
        val route: Future[Result] = testRedirectController.redirectToGroupProfileManagement(None)(groupProfileRequest)

        status(route) mustBe FOUND
        redirectLocation(route).get mustBe expectedLocation(groupProfileUrl, nonAgentRedirect)
      }

      "a redirect_uri is supplied that is not on the allowlist" in {
        stubAuthorised(authResponse(affinity = "Individual").toString)
        val route: Future[Result] = testRedirectController.redirectToGroupProfileManagement(Some("https://nonallowlisted.com/test"))(groupProfileRequest)

        status(route) mustBe FOUND
        redirectLocation(route).get mustBe expectedLocation(groupProfileUrl, nonAgentRedirect)
      }
    }

    "redirect to the group profile url with the agent redirect_uri" when {
      "the user has Agent affinity" in {
        stubAuthorised(authResponse(affinity = "Agent").toString)
        val route: Future[Result] = testRedirectController.redirectToGroupProfileManagement(None)(groupProfileRequest)

        status(route) mustBe FOUND
        redirectLocation(route).get mustBe expectedLocation(groupProfileUrl, agentRedirect)
      }
    }

    "redirect to sign in" when {
      "auth returns no group profile" in {
        stubAuthorised(authResponse(groupProfile = None, affinity = "Organisation").toString)
        val route: Future[Result] = testRedirectController.redirectToGroupProfileManagement(None)(groupProfileRequest)

        status(route) mustBe SEE_OTHER
        redirectLocation(route).get must include("/bas-gateway/sign-in")
        redirectLocation(route).get must include("continue_url=%2Fprofile-management-group")
      }

      Seq("MissingBearerToken", "BearerTokenExpired", "InvalidBearerToken", "SessionRecordNotFound").foreach { reason =>
        s"auth returns $reason" in {
          stubUnauthorised(reason)
          val route: Future[Result] = testRedirectController.redirectToGroupProfileManagement(None)(groupProfileRequest)

          status(route) mustBe SEE_OTHER
          redirectLocation(route).get must include("/bas-gateway/sign-in")
          redirectLocation(route).get must include("continue_url=%2Fprofile-management-group")
        }
      }
    }
  }
}