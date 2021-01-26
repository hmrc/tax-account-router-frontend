import sbt._

object AppDependencies {

  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"   %% "bootstrap-frontend-play-27" % "2.25.0",
    "uk.gov.hmrc"   %% "http-caching-client" % "9.1.0-play-27",
    "uk.gov.hmrc"   %% "mongo-caching"       % "6.16.0-play-27",
    "uk.gov.hmrc"   %% "auth-client"         % "3.0.0-play-27",
    "uk.gov.hmrc"   %% "play-ui"             % "8.21.0-play-27",
    "uk.gov.hmrc"   %% "govuk-template"      % "5.61.0-play-27",
    "org.typelevel" %% "cats"                % "0.9.0"
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "com.typesafe.play"      %% "play-test"                % PlayVersion.current % scope,
      "org.scalatest"           %% "scalatest"          % "3.0.9" % scope,
      "org.scalatestplus.play" %% "scalatestplus-play"       % "4.0.3"             % scope,
      "org.scalacheck"          %% "scalacheck"         % "1.14.3" % scope,
      "org.pegdown"             % "pegdown"                  % "1.6.0"             % scope,
      "org.jsoup"               % "jsoup" % "1.13.1" % scope,
      "com.github.tomakehurst"  % "wiremock-jre8" % "2.27.1" % scope,
      "org.mockito"             % "mockito-core" % "3.4.6" % scope,
      "me.scf37.expecty"       %% "expecty"                  % "1.0.2"             % scope
    )
  }


  object Test extends TestDependencies("test")

  object IntegrationTest extends TestDependencies("it")

  def apply(): Seq[ModuleID] = compile ++ Test.test ++ IntegrationTest.test
}
