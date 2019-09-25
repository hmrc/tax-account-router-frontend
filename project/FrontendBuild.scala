import sbt._

object FrontendBuild extends Build with MicroService {

  override val appName = "tax-account-router-frontend"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()

}

private object AppDependencies {

  import play.core.PlayVersion

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"   %% "frontend-bootstrap"  % "12.9.0",
    "uk.gov.hmrc"   %% "http-caching-client" % "8.5.0-play-25",
    "uk.gov.hmrc"   %% "mongo-caching"       % "6.6.0-play-25",
    "org.typelevel" %% "cats"                % "0.9.0"
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "com.typesafe.play"      %% "play-test"                % PlayVersion.current % scope,
      "uk.gov.hmrc"            %% "reactivemongo-test"       % "4.15.0-play-25"    % scope,
      "uk.gov.hmrc"            %% "service-integration-test" % "0.9.0-play-25"     % scope,
      "org.scalatestplus.play" %% "scalatestplus-play"       % "2.0.1"             % scope,
      "org.pegdown"             % "pegdown"                  % "1.6.0"             % scope,
      "org.jsoup"               % "jsoup"                    % "1.10.2"            % scope,
      "com.github.tomakehurst"  % "wiremock"                 % "2.5.1"             % scope,
      "org.mockito"             % "mockito-core"             % "1.9.5"             % scope,
      "me.scf37.expecty"       %% "expecty"                  % "1.0.2"             % scope
    )
  }

  object Test extends TestDependencies("test")

  object IntegrationTest extends TestDependencies("it")

  def apply(): Seq[ModuleID] = compile ++ Test.test ++ IntegrationTest.test
}
