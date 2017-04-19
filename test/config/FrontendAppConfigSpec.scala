package config

import play.api.Configuration
import play.api.test.FakeApplication
import play.api.test.Helpers.running
import uk.gov.hmrc.play.test.UnitSpec

class FrontendAppConfigSpec extends UnitSpec {

  "getThrottlingConfig" should {
    "return configuration for throttling location" in {

      val testConfiguration = Map[String, Any](
        "throttling.locations.some-location.key1" -> "value1",
        "throttling.locations.some-location.key2" -> "value2"
      )

      val fakeApplication = FakeApplication(additionalConfiguration = testConfiguration)
      running(fakeApplication) {

        val configuration = FrontendAppConfig.getThrottlingConfig("some-location")

        val expectedConfiguration = Configuration.from(Map[String, Any](
          "key1" -> "value1",
          "key2" -> "value2"
        ))

        configuration shouldBe expectedConfiguration
      }
    }
  }
}
