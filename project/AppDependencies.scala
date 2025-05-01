import sbt.*

object AppDependencies {

  import play.core.PlayVersion

  val boostrapVersion = "9.11.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-frontend-play-30"   % boostrapVersion,
    "uk.gov.hmrc"                  %% "play-frontend-hmrc-play-30"   % "12.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "org.playframework"            %% "play-test"                % PlayVersion.current,
    "org.mockito"                  % "mockito-core"              % "5.14.2",
    "org.scalatestplus.play"       %% "scalatestplus-play"       % "7.0.1",
    "org.scalatestplus"            %% "mockito-3-12"             % "3.2.10.0",
    "uk.gov.hmrc"                  %% "bootstrap-test-play-30"   % boostrapVersion,
    "org.pegdown"                  % "pegdown"                   % "1.6.0",
    "com.github.tomakehurst"       % "wiremock-jre8"             % "3.0.1",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"     % "2.18.0"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
