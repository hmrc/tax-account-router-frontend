import sbt._

object AppDependencies {

  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"   %% "bootstrap-frontend-play-26"   % "5.2.0",
    "uk.gov.hmrc"   %% "http-caching-client" % "9.1.0-play-26",
    "uk.gov.hmrc"   %% "mongo-caching"       % "7.0.0-play-26",
    "uk.gov.hmrc"   %% "play-ui"             % "9.2.0-play-26",
    "uk.gov.hmrc"   %% "govuk-template"      % "5.55.0-play-26",
    "org.typelevel" %% "cats"                % "0.9.0"
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "com.typesafe.play"      %% "play-test"                % PlayVersion.current % scope,
      "org.scalatest"          %% "scalatest"                % "3.0.8"             % "test",
      "uk.gov.hmrc"            %% "service-integration-test" % "0.13.0-play-26"    % scope,
      "org.mockito"             % "mockito-core"             % "3.10.0"            % scope,
      "org.scalatestplus.play" %% "scalatestplus-play"       % "3.1.3"             % scope,
      "org.pegdown"             % "pegdown"                  % "1.6.0"             % scope,
      "org.jsoup"               % "jsoup"                    % "1.13.1"            % scope,
      "com.github.tomakehurst"  % "wiremock-jre8"            % "2.23.2"            % scope
    )
  }

  object Test extends TestDependencies("test")

  object IntegrationTest extends TestDependencies("it")

  def apply(): Seq[ModuleID] = compile ++ Test.test ++ IntegrationTest.test
}
