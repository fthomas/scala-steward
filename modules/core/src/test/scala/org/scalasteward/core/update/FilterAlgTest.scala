package org.scalasteward.core.update

import cats.implicits._
import org.scalasteward.core.mock.MockContext.filterAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.model.Update
import org.scalasteward.core.repoconfig.{RepoConfig, UpdatePattern, UpdatesConfig}
import org.scalasteward.core.update.FilterAlg.{BadVersions, NonSnapshotToSnapshotUpdate}
import org.scalasteward.core.util.Nel
import org.scalatest.{FunSuite, Matchers}

class FilterAlgTest extends FunSuite with Matchers {
  test("ignoreNonSnapshotToSnapshotUpdate: SNAP -> SNAP") {
    val update = Update.Single("org.scalatest", "scalatest", "3.0.8-SNAP2", Nel.of("3.1.0-SNAP10"))
    FilterAlg.ignoreNonSnapshotToSnapshotUpdate(update) shouldBe Right(update)
  }

  test("ignoreNonSnapshotToSnapshotUpdate: RC -> SNAP") {
    val update = Update.Single("org.scalatest", "scalatest", "3.0.8-RC2", Nel.of("3.1.0-SNAP10"))
    FilterAlg.ignoreNonSnapshotToSnapshotUpdate(update) shouldBe
      Left(NonSnapshotToSnapshotUpdate(update))
  }

  test("removeBadVersions: update without bad version") {
    val update = Update.Single("com.jsuereth", "sbt-pgp", "1.1.0", Nel.of("1.1.2", "2.0.0"))
    FilterAlg.removeBadVersions(update) shouldBe Right(update)
  }

  test("removeBadVersions: update with bad version") {
    val update = Update.Single("com.jsuereth", "sbt-pgp", "1.1.2-1", Nel.of("1.1.2", "2.0.0"))
    FilterAlg.removeBadVersions(update) shouldBe Right(update.copy(newerVersions = Nel.of("2.0.0")))
  }

  test("removeBadVersions: update with only bad versions") {
    val update = Update.Single("org.http4s", "http4s-dsl", "0.18.0", Nel.of("0.19.0"))
    FilterAlg.removeBadVersions(update) shouldBe Left(BadVersions(update))
  }

  test("ignore update via config updates.ignore") {
    val update1 = Update.Single("org.http4s", "http4s-dsl", "0.17.0", Nel.of("0.18.0"))
    val update2 = Update.Single("eu.timepit", "refined", "0.8.0", Nel.of("0.8.1"))
    val config =
      RepoConfig(UpdatesConfig(ignore = List(UpdatePattern("eu.timepit", Some("refined"), None))))

    val initialState = MockState.empty
    val (state, filtered) =
      filterAlg.localFilterMany(config, List(update1, update2)).run(initialState).unsafeRunSync()

    filtered shouldBe List(update1)
    state shouldBe initialState.copy(
      logs = Vector(
        (None, "Ignore eu.timepit:refined : 0.8.0 -> 0.8.1 (reason: ignored by config)")
      )
    )
  }

  test("ignore update via config updates.allow") {
    val update1 = Update.Single("org.http4s", "http4s-dsl", "0.17.0", Nel.of("0.18.0"))
    val update2 = Update.Single("eu.timepit", "refined", "0.8.0", Nel.of("0.8.1"))

    val config = RepoConfig(
      updates = UpdatesConfig(
        allow = List(
          UpdatePattern("org.http4s", None, Some("0.17")),
          UpdatePattern("eu.timepit", Some("refined"), Some("0.8"))
        )
      )
    )

    val filtered = filterAlg
      .localFilterMany(config, List(update1, update2))
      .runA(MockState.empty)
      .unsafeRunSync()

    filtered shouldBe List(update2)
  }
}
