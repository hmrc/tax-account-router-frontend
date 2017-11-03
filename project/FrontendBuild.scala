import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

object FrontendBuild extends Build with MicroService {

  override val appName = "tax-account-router-frontend"

  override lazy val plugins: Seq[Plugins] = Seq(
    SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin
  )

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.core.PlayVersion

  val compile = Seq(
    "uk.gov.hmrc" %% "frontend-bootstrap" % "8.10.0",
    "uk.gov.hmrc" %% "http-caching-client" % "7.0.0",
    "uk.gov.hmrc" %% "mongo-caching" % "5.2.0",
    "org.typelevel" %% "cats" % "0.9.0"
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "org.scalatest" %% "scalatest" % "3.0.0" % scope,
      "org.pegdown" % "pegdown" % "1.6.0" % scope,
      "org.jsoup" % "jsoup" % "1.10.2" % scope,
      "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
      "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % scope,
      "com.github.tomakehurst" % "wiremock" % "2.5.1" % scope,
      "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope,
      "com.codeborne" % "phantomjsdriver" % "1.2.1" % scope,
      "org.mockito" % "mockito-core" % "1.9.5" % scope,
      "uk.gov.hmrc" %% "reactivemongo-test" % "2.0.0" % scope,
      "me.scf37.expecty" %% "expecty" % "1.0.2" % scope
    )
  }

  object Test extends TestDependencies("test")

  object IntegrationTest extends TestDependencies("it")

  def apply() = compile ++ Test.test ++ IntegrationTest.test
}





