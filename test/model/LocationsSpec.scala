package model

import config.AppConfig
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

class LocationsSpec extends UnitSpec with MockitoSugar {

  "locations" should {

    "return Location when config has no missing keys" in {
      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.getLocationConfig("pta", "name")).thenReturn(Some("some-name"))
      when(mockAppConfig.getLocationConfig("pta", "url")).thenReturn(Some("some-url"))

      val locations = new Locations {
        val appConfig = mockAppConfig
      }

      val result = locations.PersonalTaxAccount

      result shouldBe Location("some-name", "some-url")
    }

    "return Location when config has no missing url key" in {
      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.getLocationConfig("tax-account-router", "name")).thenReturn(Some("some-name"))
      when(mockAppConfig.getLocationConfig("tax-account-router", "url")).thenReturn(None)

      val locations = new Locations {
        val appConfig = mockAppConfig
      }

      val caught = intercept[RuntimeException] {
        locations.TaxAccountRouterHome
      }

      caught shouldNot be(null)
    }

    "return Location when config has no missing name key" in {
      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.getLocationConfig("bta", "name")).thenReturn(None)

      val locations = new Locations {
        val appConfig = mockAppConfig
      }

      val caught = intercept[RuntimeException] {
        locations.BusinessTaxAccount
      }

      caught shouldNot be(null)
    }
  }
}
