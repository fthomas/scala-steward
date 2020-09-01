package org.scalasteward.core.buildtool.sbt

import cats.data.StateT
import org.scalasteward.core.buildtool.sbt.command._
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SbtAlgTest extends AnyFunSuite with Matchers {
  test("addGlobalPlugins") {
    sbtAlg
      .addGlobalPlugins(StateT.modify(_.exec(List("fa"))))
      .runS(MockState.empty)
      .unsafeRunSync() shouldBe MockState.empty.copy(
      commands = Vector(
        List("read", "classpath:org/scalasteward/sbt/plugin/StewardPlugin.scala"),
        List("write", "/tmp/steward/.sbt/0.13/plugins/StewardPlugin.scala"),
        List("write", "/tmp/steward/.sbt/1.0/plugins/StewardPlugin.scala"),
        List("fa"),
        List("rm", "-rf", "/tmp/steward/.sbt/1.0/plugins/StewardPlugin.scala"),
        List("rm", "-rf", "/tmp/steward/.sbt/0.13/plugins/StewardPlugin.scala")
      ),
      logs = Vector((None, "Add global sbt plugins")),
      files = Map.empty
    )
  }

  test("getDependenciesAndResolvers") {
    val repo = Repo("typelevel", "cats")
    val repoDir = config.workspace / repo.show
    val files = Map(repoDir / "project" / "build.properties" -> "sbt.version=1.2.6")
    val initial = MockState.empty.copy(files = files)
    val state = sbtAlg.getDependencies(repo).runS(initial).unsafeRunSync()
    state shouldBe initial.copy(
      commands = Vector(
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          s";$setOffline;$crossStewardDependencies;$reloadPlugins;$stewardDependencies"
        ),
        List("read", s"$repoDir/project/build.properties")
      )
    )
  }

  test("runMigrations") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.show
    val migrations = Nel.of(
      Migration(
        GroupId("co.fs2"),
        Nel.of("fs2-core"),
        Version("1.0.0"),
        Nel.of("github:functional-streams-for-scala/fs2/v1?sha=v1.0.5"),
        None,
        None
      )
    )
    val state = sbtAlg.runMigrations(repo, migrations).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("write", "/tmp/steward/.sbt/0.13/plugins/scala-steward-scalafix.sbt"),
        List("write", "/tmp/steward/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          s";$scalafixEnable;$scalafixAll github:functional-streams-for-scala/fs2/v1?sha=v1.0.5"
        ),
        List("rm", "-rf", "/tmp/steward/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        List("rm", "-rf", "/tmp/steward/.sbt/0.13/plugins/scala-steward-scalafix.sbt")
      )
    )
  }

  test("runMigrations: migration with scalacOptions") {
    val repo = Repo("fthomas", "scala-steward")
    val repoDir = config.workspace / repo.show
    val migrations = Nel.of(
      Migration(
        GroupId("org.typelevel"),
        Nel.of("cats-core"),
        Version("2.2.0"),
        Nel.of("github:cb372/cats/Cats_v2_2_0?sha=235bd7c92e431ab1902db174cf4665b05e08f2f1"),
        None,
        Some(Nel.of("-P:semanticdb:synthetics:on"))
      )
    )
    val state = sbtAlg.runMigrations(repo, migrations).runS(MockState.empty).unsafeRunSync()

    state shouldBe MockState.empty.copy(
      commands = Vector(
        List("write", "/tmp/steward/.sbt/0.13/plugins/scala-steward-scalafix.sbt"),
        List("write", "/tmp/steward/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        List("write", s"$repoDir/scala-steward-scalafix-options.sbt"),
        List(
          "TEST_VAR=GREAT",
          "ANOTHER_TEST_VAR=ALSO_GREAT",
          repoDir.toString,
          "firejail",
          s"--whitelist=$repoDir",
          "sbt",
          "-batch",
          "-no-colors",
          s";$scalafixEnable;$scalafixAll github:cb372/cats/Cats_v2_2_0?sha=235bd7c92e431ab1902db174cf4665b05e08f2f1"
        ),
        List("rm", "-rf", s"$repoDir/scala-steward-scalafix-options.sbt"),
        List("rm", "-rf", "/tmp/steward/.sbt/1.0/plugins/scala-steward-scalafix.sbt"),
        List("rm", "-rf", "/tmp/steward/.sbt/0.13/plugins/scala-steward-scalafix.sbt")
      )
    )
  }
}
