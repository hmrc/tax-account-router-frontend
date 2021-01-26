package router

import com.github.tomakehurst.wiremock.client.WireMock.{getRequestedFor, urlEqualTo, urlMatching, verify}
import connector.SelfAssessmentConnector
import controllers.RouterController
import model.AffinityGroupValue
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.{GivenWhenThen, MustMatchers, WordSpec}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.test.WsTestClient
import support.TARIntegrationTest
import support.stubs.{CommonStubs, StubHelper}
import uk.gov.hmrc.http.HeaderCarrier

class RouterFeature extends WordSpec with MustMatchers with TARIntegrationTest with GivenWhenThen with CommonStubs with StubHelper with WsTestClient {

  val controller = inject[RouterController]
  lazy val connector: SelfAssessmentConnector = inject[SelfAssessmentConnector]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  "Router feature" when {
    "a user is not authenticated and should be routed to GG login page" in {
      stubNotAuthenticatedUser()
      stubOut(urlMatching("/account"), "PTA Home Page")

      withClient { ws =>
        val result = await(ws.url(s"http://localhost:$port/account").withFollowRedirects(false).get())
        result.status shouldBe 303
        result.header("Location").get.contains("/gg/sign-in?continue=/account") shouldBe true
      }
    }

    "a user logged in through verify should be redirected to PTA" in {
      setVerifyUser()
      stubOut(urlMatching("/personal-account"), "PTA Home Page")

      verify(0, getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
    "a user logged in through GG with any business account be redirected to BTA" in {
      setGGUser()
      stubRetrievalALLEnrolments("enr1")

      stubOut(urlMatching("/business-account"), "PTA Home Page")

      verify(0, getRequestedFor(urlEqualTo("/auth/enrolments-uri")))

      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
    "a user logged in through GG and sa returning 500 should be redirected to BTA" in {
      setGGUser()
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()
      stubSaReturnToReturn500(saUtr)

      connector.lastReturn(saUtr)

      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }
    "a user logged in through GG with self assessment enrolments and no previous returns should be redirected to BTA" in {
      setGGUser()
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()
      stubSaReturnWithNoPreviousReturns(saUtr)

      connector.lastReturn(saUtr)

      stubOut(urlMatching("/business-account"), "BTA Home Page")

      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }
    "a user logged in through GG and Auth returning 500 on GET enrolments should be redirected to BTA" in {
      setGGUser()
      stubRetrievalALLEnrolments(responsive = false)

      stubOut(urlMatching("/business-account"), "BTA Home Page")

      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
    "a user logged in through GG with self assessment enrolments and in a partnership should be redirected to BTA" in {
      setGGUser()
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("partnership"))

      connector.lastReturn(saUtr)

      stubOut(urlMatching("/business-account"), "BTA Home Page")

      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }
    "a user logged in through GG with self assessment enrolments and self employed should be redirected to BTA" in {
      setGGUser()
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()
      stubSaReturn(saUtr, previousReturns = true, supplementarySchedules = List("self_employment"))

      connector.lastReturn(saUtr)

      stubOut(urlMatching("/business-account"), "BTA Home Page")

      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }
    "a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with no NINO should be redirected to BTA" in {
      setGGUser()
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()
      stubSaReturn(saUtr, previousReturns = true)

      connector.lastReturn(saUtr)

      stubOut(urlMatching("/business-account"), "BTA Home Page")

      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }
    "a user logged in through GG with self assessment enrolments and has previous returns and not in a partnership and not self employed and with NINO should be redirected to PTA" in {
      setGGUser()
      stubRetrievalALLEnrolments()
      stubRetrievalSAUTR()
      stubRetrievalNINO()
      stubSaReturn(saUtr, previousReturns = true)

      connector.lastReturn(saUtr)

      stubOut(urlMatching("/personal-account"), "PTA Home Page")

      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      verify(getRequestedFor(urlEqualTo(s"/sa/individual/$saUtr/return/last")))
    }
    "a user logged in through GG and has no sa and no business enrolment with individual affinity group and inactive enrolments should be redirected to BTA" in {
      setGGUser()
      stubRetrievalALLEnrolments()
      stubRetrievalAffinityGroup(AffinityGroupValue.INDIVIDUAL)

      stubOut(urlMatching("/business-account"), "BTA Home Page")

      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
    "a user logged in through GG and has no sa and no business enrolment with individual affinity group and no inactive enrolments should be redirected to PTA" in {
      setGGUser()
      stubRetrievalALLEnrolments()
      stubRetrievalAffinityGroup(AffinityGroupValue.INDIVIDUAL)

      stubOut(urlMatching("/personal-account"), "PTA Home Page")

      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
    "a user logged in through GG and has no sa and no business enrolment and no inactive enrolments and affinity group not available should be redirected to BTA" in {
      setGGUser()
      stubRetrievalALLEnrolments(hasEnrolments = false)
      stubRetrievalAffinityGroup(ready = false)

      stubOut(urlMatching("/personal-account"), "PTA Home Page")

      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
    "a user logged in through One Time Login or Privileged Access with no enrolments should go to BTA" in {
      stubAuthenticatedUser()
      stubRetrievalInternalId()

      stubRetrievalALLEnrolments(hasEnrolments = false)

      stubOut(urlMatching("/business-account"), "PTA Home Page")

      verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))

      verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
    }
  }
}


//
//  scenario("a user logged in through One Time Login or Privileged Access with no enrolments should go to BTA") {
//
//    Given("a user logged in through One Time Login or Privileged Access")
//    stubAuthenticatedUser()
//    stubRetrievalInternalId()
//
//    And("the user has no inactive enrolments")
//    stubRetrievalALLEnrolments(hasEnrolments = false)
//
//    createStubs(BtaHomeStubPage)
//
//    When("the user hits the router")
//    go(RouterRootPath)
//
//    Then("the user should be routed to BTA Home Page")
//    on(BtaHomePage)
//
//    And("user's details should not be fetched from User Details")
//    verify(0, getRequestedFor(urlEqualTo("/user-details-uri")))
//
//    And("Sa micro service should not be invoked")
//    verify(0, getRequestedFor(urlMatching("/sa/individual/.[^\\/]+/return/last")))
//  }
//
