package org.scalasteward.core.scalafix

import org.http4s.Uri
import org.scalasteward.core.application.Config.ScalafixCfg
import org.scalasteward.core.data.{GroupId, Version}
import org.scalasteward.core.io.FileAlgTest.ioFileAlg
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.scalafix.MigrationsLoaderTest.mockState
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MigrationsLoaderTest extends AnyFunSuite with Matchers {
  val migrationsUri: Uri = Uri.unsafeFromString("/tmp/scala-steward/extra-migrations.conf")
  val migrationsContent: String =
    """|migrations = [
       |  {
       |    groupId: "org.ice.cream",
       |    artifactIds: ["yumyum-.*"],
       |    newVersion: "1.0.0",
       |    rewriteRules: ["awesome rewrite rule"],
       |    doc: "https://scalacenter.github.io/scalafix/"
       |  }
       |]""".stripMargin
  val migration: Migration = Migration(
    GroupId("org.ice.cream"),
    Nel.of("yumyum-.*"),
    Version("1.0.0"),
    Nel.of("awesome rewrite rule"),
    Some("https://scalacenter.github.io/scalafix/"),
    None
  )

  test("loadAll: without extra file, without defaults") {
    val migrations = migrationsLoader
      .loadAll(ScalafixCfg(Nil, disableDefaults = true))
      .runA(mockState)
      .unsafeRunSync()
    migrations.size shouldBe 0
  }

  test("loadAll: without extra file, with defaults") {
    val migrations = migrationsLoader
      .loadAll(ScalafixCfg(Nil, disableDefaults = false))
      .runA(mockState)
      .unsafeRunSync()
    migrations.size should be > 0
  }

  test("loadAll: with extra file, without defaults") {
    val initialState = mockState.addUri(migrationsUri, migrationsContent)
    val migrations = migrationsLoader
      .loadAll(ScalafixCfg(List(migrationsUri), disableDefaults = true))
      .runA(initialState)
      .unsafeRunSync()
    migrations shouldBe List(migration)
  }

  test("loadAll: with extra file, with defaults") {
    val initialState = mockState.addUri(migrationsUri, migrationsContent)
    val migrations = migrationsLoader
      .loadAll(ScalafixCfg(List(migrationsUri), disableDefaults = false))
      .runA(initialState)
      .unsafeRunSync()
    migrations.size should be > 1
    migrations should contain(migration)
  }

  test("loadAll: malformed extra file") {
    val initialState = mockState.addUri(migrationsUri, """{"key": "i'm not a valid Migration}""")
    val migrations = migrationsLoader
      .loadAll(ScalafixCfg(List(migrationsUri), disableDefaults = false))
      .runA(initialState)
      .attempt
      .unsafeRunSync()
    migrations.isLeft shouldBe true
  }
}

object MigrationsLoaderTest {
  val mockState: MockState = MockState.empty.addUri(
    MigrationsLoader.defaultScalafixMigrationsUrl,
    ioFileAlg.readResource("scalafix-migrations.conf").unsafeRunSync()
  )
}
