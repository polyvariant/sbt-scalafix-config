ThisBuild / tlBaseVersion := "0.1"
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
val scala3 = "3.8.4"

ThisBuild / scalaVersion := scala212
ThisBuild / crossScalaVersions := Seq(scala212, scala3)
ThisBuild / tlJdkRelease := None
ThisBuild / tlFatalWarnings := false

ThisBuild / mergifyStewardConfig ~= (_.map(_.withMergeMinors(true)))

// Run the sbt plugin's scripted tests in CI, after the normal build. The plugin builds only
// on sbt 1.x / Scala 2.12 (sbt-scalafix has no sbt 2.0 artifact), so this runs once, on the
// 2.12 matrix row, without an `++` cross prefix.
ThisBuild / githubWorkflowBuildPostamble += WorkflowStep.Sbt(
  List("sbtPlugin/scripted"),
  name = Some("Scripted tests"),
  cond = Some("matrix.scala == '2.12'"),
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

// The plugin builds only on sbt 1.x / Scala 2.12: sbt-scalafix does not publish an sbt 2.0
// artifact. `core` is consumed on its 2.12 build.
lazy val sbtPlugin = project
  .dependsOn(core)
  .settings(
    name := "sbt-scalafix-config",
    crossScalaVersions := Seq(scala212),
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
