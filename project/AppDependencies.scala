import sbt._

object AppDependencies {

  import play.core.PlayVersion

  val boostrapVersion = "7.12.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-frontend-play-28"   % boostrapVersion,
    "uk.gov.hmrc"                  %% "play-frontend-hmrc"           % "6.2.0-play-28"
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "com.typesafe.play"            %% "play-test"                % PlayVersion.current % scope,
      "org.mockito"                  % "mockito-core"              % "5.0.0"             % scope,
      "org.scalatestplus.play"       %% "scalatestplus-play"       % "5.1.0"             % scope,
      "org.scalatestplus"            %% "mockito-3-12"             % "3.2.10.0"          % scope,
      "uk.gov.hmrc"                  %% "bootstrap-test-play-28"   % boostrapVersion            % scope,
      "org.pegdown"                  % "pegdown"                   % "1.6.0"             % scope,
      "com.github.tomakehurst"       % "wiremock-jre8"             % "2.35.0"            % scope,
      "com.fasterxml.jackson.module" %% "jackson-module-scala"     % "2.14.1"            % scope
    )
  }

  object Test extends TestDependencies("test")

  object IntegrationTest extends TestDependencies("it")

  def apply(): Seq[ModuleID] = compile ++ Test.test ++ IntegrationTest.test
}
