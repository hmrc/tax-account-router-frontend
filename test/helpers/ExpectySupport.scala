package helpers

trait ExpectySupport {
  import org.expecty.Expecty

  val expect = new Expecty()
  val expectAll = new Expecty(failEarly = false)
}
