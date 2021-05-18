import play.sbt.PlayImport.PlayKeys.playDefaultPort
import sbt.Keys.scalacOptions
import sbt._
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.sbtsettingkeys.Keys.isPublicArtefact

val appName = "tax-account-router-frontend"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtDistributablesPlugin) : _*)
  .settings(majorVersion := 1)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(playDefaultPort := 9280)
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
    testGrouping in IntegrationTest := TestPhases.oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false
  )
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)



