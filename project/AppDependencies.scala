import sbt._

object AppDependencies {

  import play.core.PlayVersion

  val boostrapVersion = "8.6.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-frontend-play-30"   % boostrapVersion,
    "uk.gov.hmrc"                  %% "play-frontend-hmrc-play-30"           % "9.10.0"
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "org.playframework"            %% "play-test"                % PlayVersion.current % scope,
      "org.mockito"                  % "mockito-core"              % "5.12.0"             % scope,
      "org.scalatestplus.play"       %% "scalatestplus-play"       % "7.0.1"             % scope,
      "org.scalatestplus"            %% "mockito-3-12"             % "3.2.10.0"          % scope,
      "uk.gov.hmrc"                  %% "bootstrap-test-play-30"   % boostrapVersion            % scope,
      "org.pegdown"                  % "pegdown"                   % "1.6.0"             % scope,
      "com.github.tomakehurst"       % "wiremock-jre8"             % "3.0.1"            % scope,
      "com.fasterxml.jackson.module" %% "jackson-module-scala"     % "2.17.0"            % scope

    )
  }

  object Test extends TestDependencies("test")

  object IntegrationTest extends TestDependencies("it")

  def apply(): Seq[ModuleID] = compile ++ Test.test ++ IntegrationTest.test
}
