import sbt._

object AppDependencies {

  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"   %% "bootstrap-play-26"   % "1.14.0",
    "uk.gov.hmrc"   %% "http-caching-client" % "9.1.0-play-26",
    "uk.gov.hmrc"   %% "mongo-caching"       % "6.15.0-play-26",
    "uk.gov.hmrc"   %% "auth-client"         % "3.0.0-play-26",
    "uk.gov.hmrc"   %% "play-ui"             % "8.11.0-play-26",
    "uk.gov.hmrc"   %% "govuk-template"      % "5.55.0-play-26",
    "org.typelevel" %% "cats"                % "0.9.0"
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "com.typesafe.play"      %% "play-test"                % PlayVersion.current % scope,
      "uk.gov.hmrc"            %% "service-integration-test" % "0.12.0-play-26"     % scope,
      "org.scalatestplus.play" %% "scalatestplus-play"       % "3.1.0"             % scope,
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
