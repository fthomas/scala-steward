import com.typesafe.sbt.packager.docker._
import sbtcrossproject.{CrossProject, CrossType, Platform}

/// variables

val groupId = "org.scala-steward"
val projectName = "scala-steward"
val rootPkg = groupId.replace("-", "")
val gitHubOwner = "scala-steward-org"

val moduleCrossPlatformMatrix: Map[String, List[Platform]] = Map(
  "benchmark" -> List(JVMPlatform),
  "core" -> List(JVMPlatform),
  "sbt-plugin" -> List(JVMPlatform),
  "mill-plugin" -> List(JVMPlatform)
)

val Scala212 = "2.12.10"
val Scala213 = "2.13.5"
val Scala3 = "3.0.0-RC3"

/// sbt-github-actions configuration

ThisBuild / crossScalaVersions := Seq(Scala212, Scala213, Scala3)
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch("master")),
  RefPredicate.StartsWith(Ref.Tag("v"))
)
ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Run(
    List("sbt ci-release"),
    name = Some("Publish JARs"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  ),
  WorkflowStep.Run(
    List(
      "docker login -u ${{ secrets.DOCKER_USERNAME }} -p ${{ secrets.DOCKER_PASSWORD }}",
      "sbt core/docker:publish"
    ),
    name = Some("Publish Docker image")
  )
)
ThisBuild / githubWorkflowJavaVersions := Seq("adopt@1.8", "adopt@1.11")
ThisBuild / githubWorkflowBuild :=
  Seq(
    WorkflowStep.Sbt(List("validate"), name = Some("Build project")),
    WorkflowStep.Use(UseRef.Public("codecov", "codecov-action", "v1"), name = Some("Codecov"))
  )

/// projects

lazy val root = project
  .in(file("."))
  .aggregate(benchmark.jvm, core.jvm, `sbt-plugin`.jvm, `mill-plugin`.jvm)
  .settings(commonSettings)
  .settings(noPublishSettings)

lazy val benchmark = myCrossProject("benchmark")
  .dependsOn(core)
  .enablePlugins(JmhPlugin)
  .settings(noPublishSettings)
  .settings(
    coverageEnabled := false,
    unusedCompileDependencies := Set.empty
  )

lazy val core = myCrossProject("core")
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, DockerPlugin)
  .settings(dockerSettings)
  .settings(
    crossScalaVersions := Seq(Scala213, Scala3),
    libraryDependencies ++= Seq(
      Dependencies.attoCore.cross(CrossVersion.for3Use2_13),
      Dependencies.bcprovJdk15to18,
      Dependencies.betterFiles.cross(CrossVersion.for3Use2_13),
      Dependencies.caseApp.cross(CrossVersion.for3Use2_13),
      Dependencies.catsCore.cross(CrossVersion.for3Use2_13),
      Dependencies.catsEffect.cross(CrossVersion.for3Use2_13),
      Dependencies.catsParse.cross(CrossVersion.for3Use2_13),
      Dependencies.circeConfig.cross(CrossVersion.for3Use2_13),
      Dependencies.circeGeneric.cross(CrossVersion.for3Use2_13),
      Dependencies.circeGenericExtras.cross(CrossVersion.for3Use2_13),
      Dependencies.circeParser.cross(CrossVersion.for3Use2_13),
      Dependencies.circeRefined.cross(CrossVersion.for3Use2_13),
      Dependencies.commonsIo,
      Dependencies.coursierCore.cross(CrossVersion.for3Use2_13),
      Dependencies.cron4sCore.cross(CrossVersion.for3Use2_13),
      Dependencies.fs2Core.cross(CrossVersion.for3Use2_13),
      Dependencies.fs2Io.cross(CrossVersion.for3Use2_13),
      Dependencies.http4sCirce.cross(CrossVersion.for3Use2_13),
      Dependencies.http4sClient.cross(CrossVersion.for3Use2_13),
      Dependencies.http4sCore.cross(CrossVersion.for3Use2_13),
      Dependencies.http4sOkhttpClient.cross(CrossVersion.for3Use2_13),
      Dependencies.jjwtApi,
      Dependencies.jjwtImpl % Runtime,
      Dependencies.jjwtJackson % Runtime,
      Dependencies.log4catsSlf4j.cross(CrossVersion.for3Use2_13),
      Dependencies.monocleCore.cross(CrossVersion.for3Use2_13),
      Dependencies.refined.cross(CrossVersion.for3Use2_13),
      Dependencies.scalacacheCaffeine.cross(CrossVersion.for3Use2_13),
      Dependencies.logbackClassic % Runtime,
      Dependencies.catsLaws.cross(CrossVersion.for3Use2_13) % Test,
      Dependencies.circeLiteral.cross(CrossVersion.for3Use2_13) % Test,
      Dependencies.disciplineMunit.cross(CrossVersion.for3Use2_13) % Test,
      Dependencies.http4sDsl.cross(CrossVersion.for3Use2_13) % Test,
      Dependencies.munit.cross(CrossVersion.for3Use2_13) % Test,
      Dependencies.munitCatsEffect.cross(CrossVersion.for3Use2_13) % Test,
      Dependencies.munitScalacheck.cross(CrossVersion.for3Use2_13) % Test,
      Dependencies.refinedScalacheck.cross(CrossVersion.for3Use2_13) % Test,
      Dependencies.scalacheck.cross(CrossVersion.for3Use2_13) % Test
    ),
    evictionErrorLevel := Level.Info,
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      val nativeSuffix = "\\.(?:dll|jnilib|so)$".r

      {
        case PathList(ps @ _*) if nativeSuffix.findFirstMatchIn(ps.last).isDefined =>
          MergeStrategy.first
        case PathList("org", "fusesource", _*) =>
          // (core / assembly) deduplicate: different file contents found in the following:
          // https/repo1.maven.org/maven2/jline/jline/2.14.6/jline-2.14.6.jar:org/fusesource/hawtjni/runtime/Callback.class
          // https/repo1.maven.org/maven2/org/fusesource/jansi/jansi/1.18/jansi-1.18.jar:org/fusesource/hawtjni/runtime/Callback.class
          MergeStrategy.first
        case otherwise =>
          val defaultStrategy = (assembly / assemblyMergeStrategy).value
          defaultStrategy(otherwise)
      }
    },
    buildInfoKeys := Seq[BuildInfoKey](
      organization,
      version,
      scalaVersion,
      scalaBinaryVersion,
      sbtVersion,
      BuildInfoKey.map(git.gitHeadCommit) { case (k, v) => k -> v.getOrElse("master") },
      BuildInfoKey.map(`sbt-plugin`.jvm / moduleRootPkg) { case (_, v) =>
        "sbtPluginModuleRootPkg" -> v
      },
      BuildInfoKey.map(`mill-plugin`.jvm / moduleName) { case (_, v) =>
        "millPluginModuleName" -> v
      },
      BuildInfoKey.map(`mill-plugin`.jvm / moduleRootPkg) { case (_, v) =>
        "millPluginModuleRootPkg" -> v
      }
    ),
    buildInfoPackage := moduleRootPkg.value,
    initialCommands += s"""
      import ${moduleRootPkg.value}._
      import ${moduleRootPkg.value}.data._
      import ${moduleRootPkg.value}.util.Nel
      import ${moduleRootPkg.value}.vcs.data._
      import better.files.File
      import cats.effect.ContextShift
      import cats.effect.IO
      import cats.effect.Timer
      import org.http4s.client.Client
      import org.typelevel.log4cats.Logger
      import org.typelevel.log4cats.slf4j.Slf4jLogger
      import scala.concurrent.ExecutionContext

      implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
      implicit val ioTimer: Timer[IO] = IO.timer(ExecutionContext.global)
      implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
    """,
    run / fork := true,
    Test / fork := true,
    Compile / unmanagedResourceDirectories ++= (`sbt-plugin`.jvm / Compile / unmanagedSourceDirectories).value
  )

lazy val `sbt-plugin` = myCrossProject("sbt-plugin")
  .settings(noPublishSettings)
  .settings(
    scalaVersion := Scala212,
    sbtPlugin := true
  )

lazy val `mill-plugin` = myCrossProject("mill-plugin")
  .settings(
    crossScalaVersions := Seq(Scala213, Scala212),
    libraryDependencies += Dependencies.millScalalib.value % Provided,
    scalacOptions -= "-Xfatal-warnings"
  )

/// settings

def myCrossProject(name: String): CrossProject =
  CrossProject(name, file(name))(moduleCrossPlatformMatrix(name): _*)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file(s"modules/$name"))
    .settings(
      moduleName := s"$projectName-$name",
      moduleRootPkg := s"$rootPkg.${name.replace('-', '.')}"
    )
    .settings(commonSettings)
    // workaround for https://github.com/portable-scala/sbt-crossproject/issues/74
    .settings(Seq(Compile, Test).flatMap(inConfig(_) {
      unmanagedResourceDirectories ++= {
        unmanagedSourceDirectories.value
          .map(src => (src / ".." / "resources").getCanonicalFile)
          .filterNot(unmanagedResourceDirectories.value.contains)
          .distinct
      }
    }))

ThisBuild / dynverSeparator := "-"

lazy val commonSettings = Def.settings(
  compileSettings,
  metadataSettings,
  scaladocSettings
)

lazy val compileSettings = Def.settings(
  scalaVersion := Scala213,
  doctestTestFramework := DoctestTestFramework.Munit
)

lazy val metadataSettings = Def.settings(
  name := projectName,
  organization := groupId,
  homepage := Some(url(s"https://github.com/$gitHubOwner/$projectName")),
  startYear := Some(2018),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  scmInfo := Some(
    ScmInfo(homepage.value.get, s"scm:git:https://github.com/$gitHubOwner/$projectName.git")
  ),
  headerLicense := Some(HeaderLicense.ALv2("2018-2021", "Scala Steward contributors")),
  developers := List(
    Developer(
      id = "fthomas",
      name = "Frank S. Thomas",
      email = "",
      url(s"https://github.com/fthomas")
    )
  )
)

lazy val dockerSettings = Def.settings(
  dockerBaseImage := Option(System.getenv("DOCKER_BASE_IMAGE"))
    .getOrElse("adoptopenjdk/openjdk11:alpine"),
  dockerCommands ++= {
    val getSbtVersion = sbtVersion.value
    val sbtTgz = s"sbt-$getSbtVersion.tgz"
    val sbtUrl = s"https://github.com/sbt/sbt/releases/download/v$getSbtVersion/$sbtTgz"
    val millVersion = Dependencies.millVersion.value
    val binDir = "/usr/local/bin/"
    Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "apk --no-cache add bash git ca-certificates curl maven openssh"),
      Cmd("RUN", s"wget $sbtUrl && tar -xf $sbtTgz && rm -f $sbtTgz"),
      Cmd(
        "RUN",
        s"curl -L https://github.com/lihaoyi/mill/releases/download/${millVersion.split("-").head}/$millVersion >${binDir}mill && chmod +x ${binDir}mill"
      ),
      Cmd(
        "RUN",
        s"curl -L https://git.io/coursier-cli > ${binDir}coursier && chmod +x ${binDir}coursier && coursier install scalafmt --install-dir ${binDir} && rm -rf ${binDir}coursier"
      )
    )
  },
  Docker / packageName := s"fthomas/${name.value}",
  dockerUpdateLatest := true,
  dockerEnvVars := Map("PATH" -> "/opt/docker/sbt/bin:${PATH}")
)

lazy val noPublishSettings = Def.settings(
  publish / skip := true
)

lazy val scaladocSettings = Def.settings(
  Compile / doc / scalacOptions ++= {
    val tag = s"v${version.value}"
    val tree = if (isSnapshot.value) git.gitHeadCommit.value.getOrElse("master") else tag
    Seq(
      "-doc-source-url",
      s"${scmInfo.value.get.browseUrl}/blob/${tree}€{FILE_PATH}.scala",
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath
    )
  }
)

/// setting keys

lazy val installPlugin = taskKey[Unit]("Copies StewardPlugin.scala into global plugins directory.")
installPlugin := {
  val name = "StewardPlugin.scala"
  val source = (`sbt-plugin`.jvm / Compile / sources).value.find(_.name == name).get
  val target = file(System.getProperty("user.home")) / ".sbt" / "1.0" / "plugins" / name
  IO.copyFile(source, target)
}

lazy val moduleRootPkg = settingKey[String]("").withRank(KeyRanks.Invisible)
moduleRootPkg := rootPkg

// Run Scala Steward from sbt for development and testing.
// Do not do this in production.
lazy val runSteward = taskKey[Unit]("")
runSteward := Def.taskDyn {
  val home = System.getenv("HOME")
  val projectDir = (LocalRootProject / baseDirectory).value
  val args = Seq(
    Seq("--workspace", s"$projectDir/workspace"),
    Seq("--repos-file", s"$projectDir/repos.md"),
    Seq("--default-repo-conf", s"$projectDir/default.scala-steward.conf"),
    Seq("--git-author-email", s"me@$projectName.org"),
    Seq("--vcs-login", projectName),
    Seq("--git-ask-pass", s"$home/.github/askpass/$projectName.sh"),
    Seq("--whitelist", s"$home/.cache/coursier"),
    Seq("--whitelist", s"$home/.cache/JNA"),
    Seq("--whitelist", s"$home/.cache/mill"),
    Seq("--whitelist", s"$home/.ivy2"),
    Seq("--whitelist", s"$home/.m2"),
    Seq("--whitelist", s"$home/.mill"),
    Seq("--whitelist", s"$home/.sbt")
  ).flatten.mkString(" ", " ", "")
  (core.jvm / Compile / run).toTask(args)
}.value

/// commands

def addCommandsAlias(name: String, cmds: Seq[String]) =
  addCommandAlias(name, cmds.mkString(";", ";", ""))

addCommandsAlias(
  "validate",
  Seq(
    "clean",
    "headerCheck",
    "scalafmtCheckAll",
    "scalafmtSbtCheck",
    "unusedCompileDependenciesTest",
    "coverage",
    "test",
    "coverageReport",
    "doc",
    "package",
    "packageSrc",
    "core/assembly",
    "Docker/publishLocal"
  )
)

addCommandsAlias(
  "fmt",
  Seq(
    "headerCreate",
    "scalafmtAll",
    "scalafmtSbt"
  )
)
