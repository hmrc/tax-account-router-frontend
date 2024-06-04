import play.sbt.PlayImport.PlayKeys.playDefaultPort
import sbt.Keys.scalacOptions
import sbt._
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.sbtsettingkeys.Keys.isPublicArtefact

val appName = "tax-account-router-frontend"


lazy val scoverageSettings = Seq(
  ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;config.*;.*(AuthService|BuildInfo|Routes).*;.*views.html*;.*ErrorHandler*;",
  ScoverageKeys.coverageMinimumStmtTotal := 94,
  ScoverageKeys.coverageFailOnMinimum := true,
  ScoverageKeys.coverageHighlighting := true
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtDistributablesPlugin): _*)
  .settings(majorVersion := 1)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(playDefaultPort := 9280)
  .settings(scoverageSettings: _*)
  .settings(
    scalaVersion := "2.13.12",
    libraryDependencies ++= AppDependencies()
  )
  .configs(IntegrationTest extend Test)
  .settings(inConfig(IntegrationTest)(Defaults.testSettings): _*)
  .settings(
    fork in IntegrationTest := true,
    unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest) (base => Seq(base / "it")).value,
    unmanagedResourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest) (base => Seq(base / "it" / "resources")).value,
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testGrouping in IntegrationTest := DefaultBuildSettings.oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false,
    javaOptions ++= Seq(
      "-Dlogger.resource=logback-test.xml"
    )
  )

  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)



