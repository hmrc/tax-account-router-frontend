import play.sbt.PlayImport.PlayKeys.playDefaultPort
import sbt.Keys.scalacOptions
import sbt._
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.ForkedJvmPerTestSettings.oneForkedJvmPerTest
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.sbtsettingkeys.Keys.isPublicArtefact

val appName = "tax-account-router-frontend"

val silencerVersion = "1.7.1"


lazy val scoverageSettings = Seq(
  ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;config.*;.*(AuthService|BuildInfo|Routes).*;.*views.html*;.*ErrorHandler*;",
  ScoverageKeys.coverageMinimum := 95,
  ScoverageKeys.coverageFailOnMinimum := true,
  ScoverageKeys.coverageHighlighting := true
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtDistributablesPlugin) : _*)
  .settings(majorVersion := 1)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(playDefaultPort := 9280)
  .settings(scoverageSettings: _*)
  .settings(
    scalaVersion := "2.12.13",
    libraryDependencies ++= AppDependencies(),
    parallelExecution in Test := false,
    fork in Test := false,
    retrieveManaged := true,
    evictionWarningOptions in update :=
      EvictionWarningOptions.default.withWarnScalaVersionEviction(true),
    scalacOptions += "-feature",
    scalacOptions += "-language:implicitConversions",
    isPublicArtefact := true
  )
  .configs(IntegrationTest extend Test)
  .settings(inConfig(IntegrationTest)(Defaults.testSettings) : _*)
  .settings(
    fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest)(base => Seq(base / "it")).value,
    unmanagedResourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest)(base => Seq(base / "it" / "resources")).value,
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false
  )
  .settings(
    scalacOptions += "-P:silencer:pathFilters=routes",
    scalacOptions += "-P:silencer:lineContentFilters=^[a-zA-Z]",
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
  )
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)



