package services

import model.{Location, SA, VAT}
import play.api.test.FakeApplication
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class TwoStepVerificationRuleFactoryTest extends UnitSpec with WithFakeApplication {

  override lazy val fakeApplication = FakeApplication()

  "rules" should {

    "have correct names" in new Setup {
      val names = rules.map(_.name)
      names should contain("sa")
      names should contain("sa_vat")
    }

    "have correct enrolments" in new Setup {
      val enrolments = rules.map(rule => (rule.name, rule.enrolmentCategories))
      enrolments should contain(("sa", Set(SA)))
      enrolments should contain(("sa_vat", Set(SA, VAT)))
    }

    "have correct admin optional locations" in new Setup {
      val locations = rules.map(rule => (rule.name, rule.adminLocations.optional))

      locations should contain(("sa", Location("two-step-verification", "http://localhost:9025/coafe/two-step-verification/register", Map("continue" -> "http://localhost:9020/business-account", "failure" -> "http://localhost:9020/business-account", "origin" -> "business-tax-account"))))
      locations should contain(("sa_vat", Location("set-up-extra-security", "http://localhost:9851/user-delegation/set-up-extra-security")))
    }

    "have correct admin mandatory locations" in new Setup {
      val locations = rules.map(rule => (rule.name, rule.adminLocations.mandatory))

      locations should contain(("sa", Location("two-step-verification", "http://localhost:9025/coafe/two-step-verification/register", Map("continue" -> "http://localhost:9020/business-account", "failure" -> "http://localhost:9280/account", "origin" -> "business-tax-account"))))
      locations should contain(("sa_vat", Location("set-up-extra-security", "http://localhost:9851/user-delegation/set-up-extra-security")))
    }

    "have correct assistant optional locations" in new Setup {
      val locations = rules.map(rule => (rule.name, rule.assistantLocations.optional))

      locations should contain(("sa", Location("two-step-verification", "http://localhost:9025/coafe/two-step-verification/register", Map("continue" -> "http://localhost:9020/business-account", "failure" -> "http://localhost:9020/business-account", "origin" -> "business-tax-account"))))
      locations should contain(("sa_vat", Location("business-tax-account", "http://localhost:9020/business-account")))
    }

    "have correct assistant mandatory locations" in new Setup {
      val locations = rules.map(rule => (rule.name, rule.assistantLocations.mandatory))

      locations should contain(("sa", Location("two-step-verification", "http://localhost:9025/coafe/two-step-verification/register", Map("continue" -> "http://localhost:9020/business-account", "failure" -> "http://localhost:9280/account", "origin" -> "business-tax-account"))))
      locations should contain(("sa_vat", Location("business-tax-account", "http://localhost:9020/business-account", Map())))
    }

  }

  trait Setup {
    val saEnrolments = Set("enr3", "enr4")
    val vatEnrolments = Set("enr5", "enr6")
    private val factory = new TwoStepVerificationRuleFactory {}
    lazy val rules = factory.rules
  }

}
