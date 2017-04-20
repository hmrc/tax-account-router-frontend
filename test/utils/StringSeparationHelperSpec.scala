package helpers

import uk.gov.hmrc.play.test.UnitSpec

class StringSeparationHelperSpec extends UnitSpec {

  import utils.StringSeparationHelper._

  "helper" should {
    "split strings by comma" in {
      ",, ,a,b , c, d ,  e  ,".asCommaSeparatedValues shouldBe Seq("a", "b", "c", "d", "e")
    }

    "split strings by pipe" in {
      "|| |a|b | c| d |  e  |".asPipeSeparatedValues shouldBe Seq("a", "b", "c", "d", "e")
    }
  }
}
