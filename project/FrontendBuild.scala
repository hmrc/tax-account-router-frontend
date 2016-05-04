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
    "uk.gov.hmrc" %% "frontend-bootstrap" % "6.4.0",
    "uk.gov.hmrc" %% "play-config" % "2.0.0",
    "uk.gov.hmrc" %% "play-json-logger" % "2.1.0",
    "uk.gov.hmrc" %% "play-health" % "1.1.0",
    "uk.gov.hmrc" %% "govuk-template" % "4.0.0",
    "uk.gov.hmrc" %% "play-ui" % "4.1.0",
    "uk.gov.hmrc" %% "play-authorised-frontend" % "4.5.0",
    "uk.gov.hmrc" %% "http-caching-client" % "5.3.0",
    "uk.gov.hmrc" %% "mongo-caching" % "3.0.0"
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "org.scalatest" %% "scalatest" % "2.2.5" % scope,
      "org.pegdown" % "pegdown" % "1.6.0" % scope,
      "org.jsoup" % "jsoup" % "1.8.3" % scope,
      "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
      "uk.gov.hmrc" %% "hmrctest" % "1.4.0" % scope,
      "com.github.tomakehurst" % "wiremock" % "1.56" % scope,
      "org.scalatestplus" %% "play" % "1.2.0" % scope,
      "com.codeborne" % "phantomjsdriver" % "1.2.1" % scope,
      "uk.gov.hmrc" %% "reactivemongo-test" % "1.2.0" % scope
    )
  }

  object Test extends TestDependencies("test")

  object IntegrationTest extends TestDependencies("it")

  def apply() = compile ++ Test.test ++ IntegrationTest.test
}





