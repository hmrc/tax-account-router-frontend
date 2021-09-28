import sbt._

object AppDependencies {

  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"   %% "bootstrap-frontend-play-28"   % "5.14.0",
    "uk.gov.hmrc"   %% "play-frontend-hmrc"           % "1.14.0-play-28"
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "com.typesafe.play"      %% "play-test"                % PlayVersion.current % scope,
      "org.mockito"             % "mockito-core"             % "3.10.0"            % scope,
      "org.scalatestplus.play" %% "scalatestplus-play"       % "4.0.3"             % scope,
      "org.pegdown"             % "pegdown"                  % "1.6.0"             % scope,
      "com.github.tomakehurst"  % "wiremock-jre8"            % "2.23.2"            % scope
    )
  }

  object Test extends TestDependencies("test")

  object IntegrationTest extends TestDependencies("it")

  def apply(): Seq[ModuleID] = compile ++ Test.test ++ IntegrationTest.test
}
