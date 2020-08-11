import sbt.Tests.{Group, SubProcess}
import sbt._

import scala.util.Properties

object TestPhases {

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] = {

    val browser: Option[String] = Properties.propOrNone("browser").map(value => s"-Dbrowser=$value")

    tests map {
      test =>
        val jvmOptions = Vector(browser,Some("-Dtest.name=" + test.name)).flatten
        Group(test.name, Seq(test), SubProcess(ForkOptions().withRunJVMOptions(jvmOptions)))
    }

  }
}
