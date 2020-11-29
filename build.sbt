val catsV = "2.2.0"

val catsEffectV = "2.3.0"

val http4sV = "0.21.13"

val catsEffectScalaTestV = "0.4.2"

val tapirV = "0.17.0-M9"

val kindProjectorV = "0.11.1"

val betterMonadicForV = "0.3.1"

val logbackVersion = "1.2.3"

// General Settings
inThisBuild(
  List(
    organization := "fp-in-bo",
    developers := List(
      Developer("azanin", "Alessandro Zanin", "ale.zanin90@gmail.com", url("https://github.com/azanin"))
    ),
    homepage := Some(url("https://github.com/fp-in-bo/api")),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    pomIncludeRepository := { _ => false }
  )
)

// General Settings
lazy val commonSettings = Seq(
  scalaVersion := "2.13.3",
  scalafmtOnCompile := true,
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % kindProjectorV cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % betterMonadicForV),
  libraryDependencies ++= Seq(
    "org.typelevel"               %% "cats-effect"                   % catsV,
    "org.http4s"                  %% "http4s-dsl"                    % http4sV,
    "org.http4s"                  %% "http4s-blaze-server"           % http4sV,
    "com.softwaremill.sttp.tapir" %% "tapir-core"                    % tapirV,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"           % tapirV,
    "ch.qos.logback"              % "logback-classic"                % logbackVersion % Runtime,
    "com.codecommit"              %% "cats-effect-testing-scalatest" % catsEffectScalaTestV
  )
)

// Projects
lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(name := "api")
  .settings(parallelExecution in Test := false)
  .settings(test in assembly := {})
  .settings(assemblyJarName in assembly := "fpinbo-rest-api.jar")
  .settings(assemblyMergeStrategy in assembly := {
    case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") => MergeStrategy.singleOrError
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  })
  .enablePlugins(DockerPlugin)
  .settings(dockerfile in docker := {
    // The assembly task generates a fat JAR file
    val artifact: File     = assembly.value
    val artifactTargetPath = s"/app/${artifact.name}"

    new Dockerfile {
      from("openjdk:11-jre")
      add(artifact, artifactTargetPath)
      entryPoint("java", "-jar", artifactTargetPath)
      expose(80)
      label("org.containers.image.source", "https://github.com/fp-in-bo/api")
    }
  })
  .settings(
    imageNames in docker := Seq(
      // Sets the latest tag
      ImageName(
        namespace = Some(organization.value + "/api"),
        repository = "fp-in-bo-api",
        registry = Some("ghcr.io"),
        tag = Some("latest")
      ), // Sets a name with a tag that contains the project version
      ImageName(
        namespace = Some(organization.value + "/api"),
        repository = "fp-in-bo-api",
        registry = Some("ghcr.io"),
        tag = Some("v" + version.value.replace('+', '-'))
      )
    )
  )
  .settings(
    publish in docker := Some("Github container registry" at "https://ghcr.io")
  )

lazy val tests = project
  .in(file("tests"))
  .settings(name := "tests")
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .settings(parallelExecution in IntegrationTest := false)
  .enablePlugins(NoPublishPlugin)
  .settings(parallelExecution in Test := false)
  .dependsOn(core)

lazy val api = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .aggregate(core, tests)
