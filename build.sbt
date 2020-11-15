val catsV                = "2.2.0"
val jmsV                 = "2.0.1"
val ibmMQV               = "9.2.0.1"
val activeMQV            = "2.16.0"
val catsEffectV          = "2.2.0"
val catsEffectScalaTestV = "0.4.2"
val fs2V                 = "2.4.4"
val log4catsV            = "1.1.1"
val log4jSlf4jImplV      = "2.13.3"

val kindProjectorV    = "0.11.0"
val betterMonadicForV = "0.3.1"

// General Settings
inThisBuild(
  List(
    organization := "dev.fpinbo",
    developers := List(
      Developer("azanin", "Alessandro Zanin", "ale.zanin90@gmail.com", url("https://github.com/azanin"))
    ),
    homepage := Some(url("https://github.com/fp-in-bo/api")),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    pomIncludeRepository := { _ => false }
  )
)
// Projects
lazy val api = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .aggregate(core, tests)

lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(name := "api")
  .settings(parallelExecution in Test := false)

lazy val tests = project
  .in(file("tests"))
  .settings(commonSettings: _*)
  .enablePlugins(NoPublishPlugin)
  .settings(parallelExecution in Test := false)

// General Settings
lazy val commonSettings = Seq(
  scalaVersion := "2.13.3",
  scalafmtOnCompile := true,
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % kindProjectorV cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % betterMonadicForV),
  libraryDependencies ++= Seq(
    "com.codecommit"    %% "cats-effect-testing-scalatest" % catsEffectScalaTestV % Test
  )
)
