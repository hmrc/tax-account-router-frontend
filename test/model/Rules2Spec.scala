package model

import connector.SaReturn
import helpers.SpecHelpers
import model.Location._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.test.FakeRequest
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.UnitSpec

class Rules2Spec extends UnitSpec with MockitoSugar with SpecHelpers {

  "IsInPartnershipOrSelfEmployed" should {

    implicit lazy val fakeRequest = FakeRequest()
    implicit lazy val hc: HeaderCarrier = HeaderCarrier.fromHeadersAndSession(fakeRequest.headers)

    val scenarios = evaluateUsingPlay { () =>
      Table(
        ("scenario", "schedules", "previousReturns", "expectedLocation"),
        ("with previous returns and in partnership not self employed", List("partnership"), true, Some(BusinessTaxAccount)),
        ("with previous returns and not in partnership and self employed", List("self_employment"), true, Some(BusinessTaxAccount)),
        ("with previous returns and in partnership and self employed", List("partnership", "self_employment"), true, Some(BusinessTaxAccount)),
        ("with previous returns and not in partnership nor self employed", List.empty, true, None),
        ("with previous no returns and in partnership not self employed", List("partnership"), false, None),
        ("with previous no returns and not in partnership and self employed", List("self_employment"), false, None),
        ("with previous no returns and in partnership and self employed", List("partnership", "self_employment"), false, None),
        ("with previous no returns and not in partnership nor self employed", List.empty, false, None)
      )
    }

    forAll(scenarios) { (scenario: String, schedules: List[String], previousReturns: Boolean, expectedLocation: Option[LocationType]) =>

      val authContext: AuthContext = mock[AuthContext]

      s"apply whether the user is in a partnership or is self-employed - scenario: when $scenario should go to $expectedLocation" in {
        //given
        val mockAuditContext = mock[TAuditContext]
        val ruleContext: RuleContext = mock[RuleContext]

        val saReturn: SaReturn = SaReturn(schedules, previousReturns = previousReturns)
        when(ruleContext.lastSaReturn).thenReturn(saReturn)

        //when
        val futureLocation = NewRules.hasPreviousReturnsAnd_IsSelfEmployedOrIsSelfEmployed.apply(authContext, ruleContext, mockAuditContext)

        //then
        val location: Option[LocationType] = await(futureLocation)
        location shouldBe expectedLocation

        //and
        verify(ruleContext, times(3)).lastSaReturn
      }
    }
  }
}
