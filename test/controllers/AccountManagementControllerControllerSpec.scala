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

import config.FrontendAppConfig
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class AccountManagementControllerControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with Injecting {
  implicit lazy val mcc: MessagesControllerComponents = inject[MessagesControllerComponents]
  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]

  val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/profile-management")
  val fakeRequestGroup: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/profile-management-group")
  val fakeRequestWithUri: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/profile-management?redirect_uri=https%3A%2F%2Ftestredirect.com%2Ftest")
  val fakeRequestGroupWithUri: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/profile-management-group?redirect_uri=https%3A%2F%2Ftestredirect.com%2Ftest")

  val env: Environment = Environment.simple()

  val configuration: Configuration = Configuration.load(env).withFallback(Configuration.empty) ++ Configuration(
    "bas-gateway-frontend.host"     -> "",
    "appName"                       -> "user-profile-redirect-frontend",
    "settings.agentRedirectUrl"     -> "https://local.tax.service.gov.uk/account/portal",
    "settings.redirectUrl"          -> "https://local.tax.service.gov.uk/account",
    "settings.redirectUrlAllowlist" -> Seq("https://testredirect.com/test")
  )

  val servicesConfig: ServicesConfig = new ServicesConfig(configuration)

  val appConfig = new FrontendAppConfig(configuration, servicesConfig)
  val authConnector: AuthConnector = mock(classOf[AuthConnector])

  val controller = new AccountManagementController(authConnector, appConfig, mcc)

  "GET /profile-management" should {
    "redirect to non-agent url" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(Some("http://test/profile"), Some(AffinityGroup.Individual))))
      val result = controller.redirectToProfileManagement(None)(fakeRequest)
      status(result) shouldBe Status.FOUND
      redirectLocation(result).get shouldBe "http://test/profile?redirect_uri=https%3A%2F%2Flocal.tax.service.gov.uk%2Faccount"
    }

    "redirect to agent url" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(Some("http://test/profile"), Some(AffinityGroup.Agent))))
      val result = controller.redirectToProfileManagement(None)(fakeRequest)
      status(result) shouldBe Status.FOUND
      redirectLocation(result).get shouldBe "http://test/profile?redirect_uri=https%3A%2F%2Flocal.tax.service.gov.uk%2Faccount%2Fportal"
    }

    "redirect to whitelisted url" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(Some("http://test/profile"), Some(AffinityGroup.Agent))))
      val result = controller.redirectToProfileManagement(Some("https://testredirect.com/test"))(fakeRequestWithUri)
      status(result) shouldBe Status.FOUND
      redirectLocation(result).get shouldBe "http://test/profile?redirect_uri=https%3A%2F%2Ftestredirect.com%2Ftest"
    }

    "not redirect to non-whitelisted url for agent" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(Some("http://test/profile"), Some(AffinityGroup.Agent))))
      val result = controller.redirectToProfileManagement(Some("https://nonwhitelisted.com/test"))(fakeRequestWithUri)
      status(result) shouldBe Status.FOUND
      redirectLocation(result).get shouldBe "http://test/profile?redirect_uri=https%3A%2F%2Flocal.tax.service.gov.uk%2Faccount%2Fportal"
    }

    "not redirect to non-whitelisted url for non-agent" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(Some("http://test/profile"), Some(AffinityGroup.Individual))))
      val result = controller.redirectToProfileManagement(Some("https://nonwhitelisted.com/test"))(fakeRequestWithUri)
      status(result) shouldBe Status.FOUND
      redirectLocation(result).get shouldBe "http://test/profile?redirect_uri=https%3A%2F%2Flocal.tax.service.gov.uk%2Faccount"
    }

    "redirect to login for MissingBearToken exception" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new MissingBearerToken))
      val result = controller.redirectToProfileManagement(None)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management&origin=user-profile-redirect-frontend"
    }

    "redirect to login for MissingBearToken exception with redirect uri" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new MissingBearerToken))
      val result = controller.redirectToProfileManagement(None)(fakeRequestWithUri)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management%3Fredirect_uri%3Dhttps%253A%252F%252Ftestredirect.com%252Ftest&origin=user-profile-redirect-frontend"
    }

    "redirect to login for SessionRecordNotFound exception" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new SessionRecordNotFound))
      val result = controller.redirectToProfileManagement(None)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management&origin=user-profile-redirect-frontend"
    }

    "redirect to login for BearerTokenExpired exception" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new BearerTokenExpired))
      val result = controller.redirectToProfileManagement(None)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management&origin=user-profile-redirect-frontend"
    }

    "redirect to login for InvalidBearerToken exception" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new InvalidBearerToken))
      val result = controller.redirectToProfileManagement(None)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management&origin=user-profile-redirect-frontend"
    }

    "show exception for other errors" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new InternalError))
      val result = controller.redirectToProfileManagement(None)(fakeRequest)
      ScalaFutures.whenReady(result.failed) { e =>
        e shouldBe a[InternalError]
      }
    }

    "should redirect for no profile found" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(None, Some(AffinityGroup.Individual))))
      val result = controller.redirectToProfileManagement(None)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management&origin=user-profile-redirect-frontend"
    }
  }

  "GET /group-profile-management" should {
    "redirect to non-agent url" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(Some("http://test/group-profile"), Some(AffinityGroup.Individual))))
      val result = controller.redirectToGroupProfileManagement(None)(fakeRequestGroup)
      status(result) shouldBe Status.FOUND
      redirectLocation(result).get shouldBe "http://test/group-profile?redirect_uri=https%3A%2F%2Flocal.tax.service.gov.uk%2Faccount"
    }

    "redirect to agent url" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(Some("http://test/group-profile"), Some(AffinityGroup.Agent))))
      val result = controller.redirectToGroupProfileManagement(None)(fakeRequestGroup)
      status(result) shouldBe Status.FOUND
      redirectLocation(result).get shouldBe "http://test/group-profile?redirect_uri=https%3A%2F%2Flocal.tax.service.gov.uk%2Faccount%2Fportal"
    }

    "redirect to whitelisted url" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(Some("http://test/group-profile"), Some(AffinityGroup.Individual))))
      val result = controller.redirectToGroupProfileManagement(Some("https://testredirect.com/test"))(fakeRequestWithUri)
      status(result) shouldBe Status.FOUND
      redirectLocation(result).get shouldBe "http://test/group-profile?redirect_uri=https%3A%2F%2Ftestredirect.com%2Ftest"
    }

    "not redirect to non-whitelisted url for agent" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(Some("http://test/group-profile"), Some(AffinityGroup.Agent))))
      val result = controller.redirectToGroupProfileManagement(Some("https://nonwhitelisted.com/test"))(fakeRequestWithUri)
      status(result) shouldBe Status.FOUND
      redirectLocation(result).get shouldBe "http://test/group-profile?redirect_uri=https%3A%2F%2Flocal.tax.service.gov.uk%2Faccount%2Fportal"
    }

    "not redirect to non-whitelisted url for non-agent" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(Some("http://test/group-profile"), Some(AffinityGroup.Individual))))
      val result = controller.redirectToGroupProfileManagement(Some("https://nonwhitelisted.com/test"))(fakeRequestWithUri)
      status(result) shouldBe Status.FOUND
      redirectLocation(result).get shouldBe "http://test/group-profile?redirect_uri=https%3A%2F%2Flocal.tax.service.gov.uk%2Faccount"
    }

    "redirect to login for MissingBearToken exception" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new MissingBearerToken))
      val result = controller.redirectToGroupProfileManagement(None)(fakeRequestGroup)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management-group&origin=user-profile-redirect-frontend"
    }

    "redirect to login for MissingBearToken exception with redirect uri" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new MissingBearerToken))
      val result = controller.redirectToGroupProfileManagement(None)(fakeRequestGroupWithUri)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management-group%3Fredirect_uri%3Dhttps%253A%252F%252Ftestredirect.com%252Ftest&origin=user-profile-redirect-frontend"
    }

    "redirect to login for SessionRecordNotFound exception" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new SessionRecordNotFound))
      val result = controller.redirectToGroupProfileManagement(None)(fakeRequestGroup)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management-group&origin=user-profile-redirect-frontend"
    }

    "redirect to login for BearerTokenExpired exception" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new BearerTokenExpired))
      val result = controller.redirectToGroupProfileManagement(None)(fakeRequestGroup)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management-group&origin=user-profile-redirect-frontend"
    }

    "redirect to login for InvalidBearerToken exception" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new InvalidBearerToken))
      val result = controller.redirectToGroupProfileManagement(None)(fakeRequestGroup)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management-group&origin=user-profile-redirect-frontend"
    }

    "show exception for other errors" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new InternalError))
      val result = controller.redirectToGroupProfileManagement(None)(fakeRequestGroup)
      ScalaFutures.whenReady(result.failed) { e =>
        e shouldBe a[InternalError]
      }
    }

    "should redirect for no profile found" in {
      when(authConnector.authorise(any[Predicate], any[Retrieval[Option[String] ~ Option[AffinityGroup]]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(new~(None, Some(AffinityGroup.Organisation))))
      val result = controller.redirectToGroupProfileManagement(None)(fakeRequestGroup)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result).get shouldBe "/bas-gateway/sign-in?continue_url=%2Fprofile-management-group&origin=user-profile-redirect-frontend"
    }
  }
}