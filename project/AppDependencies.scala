import sbt._

object AppDependencies {

  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-frontend-play-28"   % "5.20.0",
    "uk.gov.hmrc"                  %% "play-frontend-hmrc"           % "3.4.0-play-28"
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "com.typesafe.play"            %% "play-test"                % PlayVersion.current % scope,
      "org.mockito"                  % "mockito-core"              % "4.3.1"             % scope,
      "org.scalatestplus.play"       %% "scalatestplus-play"       % "5.1.0"             % scope,
      "org.scalatestplus"            %% "mockito-3-12"             % "3.2.10.0"          % scope,
      "uk.gov.hmrc"                  %% "bootstrap-test-play-28"   % "5.20.0"            % scope,
      "org.pegdown"                  % "pegdown"                   % "1.6.0"             % scope,
      "com.github.tomakehurst"       % "wiremock-jre8"             % "2.32.0"            % scope,
      "com.fasterxml.jackson.module" %% "jackson-module-scala"     % "2.13.1"            % scope
    )
  }

  object Test extends TestDependencies("test")

  object IntegrationTest extends TestDependencies("it")

  def apply(): Seq[ModuleID] = compile ++ Test.test ++ IntegrationTest.test
}
