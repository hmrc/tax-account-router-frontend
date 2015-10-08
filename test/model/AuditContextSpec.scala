package model

import uk.gov.hmrc.play.test.UnitSpec

class AuditContextSpec extends UnitSpec {

  "default reasons" should {

    "be the expected ones" in {

      AuditContext.defaultReasons shouldBe Map(
        "has-seen-welcome-page" -> "-",
        "has-print-preferences-set" -> "-",
        "has-business-enrolments" -> "-",
        "has-previous-returns" -> "-",
        "is-in-a-partnership" -> "-",
        "is-self-employed" -> "-"
      )
    }
  }
}
