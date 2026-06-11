ThisBuild / tlBaseVersion := "0.2"
ThisBuild / organization := "org.polyvariant"
ThisBuild / organizationName := "Polyvariant"
ThisBuild / startYear := Some(2026)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(tlGitHubDev("kubukoz", "Jakub Kozłowski"))

ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch("main")),
  RefPredicate.StartsWith(Ref.Tag("v")),
)

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))

val scala212 = "2.12.21"

// The whole build targets sbt 1.x / Scala 2.12 only. This is an sbt plugin and sbt-scalafix
// (which it builds on) ships no Scala 3 / sbt 2.0 artifact, so the plugin can only be 2.12.
// `core` stays on the same single axis too: if it were also cross-built to Scala 3, the
// 2.12-only plugin's `dependsOn(core)` would resolve `core` as a *published* artifact (the two
// live on different cross axes) and fail on a clean checkout. Nothing consumes a Scala 3
// `core` anyway — only the plugin uses it.
ThisBuild / scalaVersion := scala212
ThisBuild / crossScalaVersions := Seq(scala212)
ThisBuild / tlJdkRelease := None
ThisBuild / tlFatalWarnings := false

ThisBuild / mergifyStewardConfig ~= (_.map(_.withMergeMinors(true)))

// Run the sbt plugin's scripted tests in CI, after the normal build.
ThisBuild / githubWorkflowBuildPostamble += WorkflowStep.Sbt(
  List("sbtPlugin/scripted"),
  name = Some("Scripted tests"),
)

lazy val core = project
  .settings(
    name := "scalafix-config-core",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.4.3",
      "org.scalameta" %% "munit" % "1.3.2" % Test,
    ),
    mimaPreviousArtifacts := Set.empty,
  )

lazy val sbtPlugin = project
  .dependsOn(core)
  .settings(
    name := "sbt-scalafix-config",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.3.2" % Test
    ),
    addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.6"),
    pluginCrossBuild / sbtVersion := "1.9.8",
    scriptedLaunchOpts :=
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    scriptedBufferLog := false,
    mimaPreviousArtifacts := Set.empty,
  )
  .enablePlugins(SbtPlugin)

lazy val root = project
  .in(file("."))
  .aggregate(core, sbtPlugin)
  .enablePlugins(NoPublishPlugin)
