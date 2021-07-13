package org.scalasteward.core.vcs.data

import io.circe.syntax._
import munit.FunSuite
import org.http4s.syntax.literals._
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.sbt.data.SbtVersion
import org.scalasteward.core.data._
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.util.Nel

class NewPullRequestDataTest extends FunSuite {
  test("asJson") {
    val data = UpdateData(
      RepoData(Repo("foo", "bar"), dummyRepoCache, RepoConfig.empty),
      Repo("scala-steward", "bar"),
      Update.Single("ch.qos.logback" % "logback-classic" % "1.2.0", Nel.of("1.2.3")),
      Branch("master"),
      Sha1(Sha1.HexString.unsafeFrom("d6b6791d2ea11df1d156fe70979ab8c3a5ba3433")),
      Branch("update/logback-classic-1.2.3")
    )
    val obtained = NewPullRequestData
      .from(data, "scala-steward:update/logback-classic-1.2.3")
      .asJson
      .spaces2
    val expected =
      raw"""|{
            |  "title" : "Update logback-classic to 1.2.3",
            |  "body" : "Update ch.qos.logback:logback-classic from `1.2.0` to `1.2.3`.\n* \n\n> I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n> To skip this version update, just close this PR. If you have any feedback,\n> just mention me in the comments below.\n> Configure Scala Steward for your repository with a\n> [`.scala-steward.conf`](https://github.com/scala-steward-org/scala-steward/blob/fcb3205568718165f2edd88599e603ee21886132/docs/repo-specific-configuration.md) file.\n\nHave a fantastic day writing Scala!\n\n---\n\n<details>\n<summary>Suppress future updates</summary>\n\nAdd this to your `.scala-steward.conf` file to suppress future updates of this dependency:\n```\nupdates.ignore = [ { groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" } ]\n```\n</details>\n\n~~~\nlabels: library-update, semver-patch",
            |  "head" : "scala-steward:update/logback-classic-1.2.3",
            |  "base" : "master",
            |  "draft" : false
            |}""".stripMargin
    assertEquals(obtained, expected)
  }

  test("fromTo") {
    assertEquals(
      NewPullRequestData.fromTo(
        Update.Single("com.example" % "foo" % "1.2.0", Nel.of("1.2.3"))
      ),
      "from `1.2.0` to `1.2.3`"
    )
  }

  test("links to release notes/changelog") {
    assertEquals(NewPullRequestData.releaseNote(List.empty), None)

    assertEquals(
      NewPullRequestData.releaseNote(
        List(ReleaseRelatedUrl.CustomChangelog(uri"https://github.com/foo/foo/CHANGELOG.rst"))
      ),
      Some("[Changelog](https://github.com/foo/foo/CHANGELOG.rst)")
    )

    assertEquals(
      NewPullRequestData.releaseNote(
        List(
          ReleaseRelatedUrl.CustomChangelog(uri"https://github.com/foo/foo/CHANGELOG.rst"),
          ReleaseRelatedUrl.GitHubReleaseNotes(uri"https://github.com/foo/foo/releases/tag/v1.2.3"),
          ReleaseRelatedUrl.CustomReleaseNotes(
            uri"https://github.com/foo/bar/blob/master/ReleaseNotes.md"
          ),
          ReleaseRelatedUrl.CustomReleaseNotes(
            uri"https://github.com/foo/bar/blob/master/Releases.md"
          ),
          ReleaseRelatedUrl.VersionDiff(uri"https://github.com/foo/foo/compare/v1.2.0...v1.2.3")
        )
      ),
      Some(
        "[Changelog](https://github.com/foo/foo/CHANGELOG.rst) - [GitHub Release Notes](https://github.com/foo/foo/releases/tag/v1.2.3) - [Release Notes](https://github.com/foo/bar/blob/master/ReleaseNotes.md) - [Release Notes](https://github.com/foo/bar/blob/master/Releases.md) - [Version Diff](https://github.com/foo/foo/compare/v1.2.0...v1.2.3)"
      )
    )
  }

  test("showing artifacts with URL in Markdown format") {
    assertEquals(
      NewPullRequestData.artifactsWithOptionalUrl(
        Update.Single("com.example" % "foo" % "1.2.0", Nel.of("1.2.3")),
        Map("foo" -> uri"https://github.com/foo/foo")
      ),
      "[com.example:foo](https://github.com/foo/foo)"
    )
    assertEquals(
      NewPullRequestData.artifactsWithOptionalUrl(
        Update.Group("com.example" % Nel.of("foo", "bar") % "1.2.0", Nel.of("1.2.3")),
        Map("foo" -> uri"https://github.com/foo/foo", "bar" -> uri"https://github.com/bar/bar")
      ),
      """
        |* [com.example:foo](https://github.com/foo/foo)
        |* [com.example:bar](https://github.com/bar/bar)
        |
        |""".stripMargin
    )
  }

  test("migrationNote: when no migrations") {
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(List.empty)

    assertEquals(label, None)
    assertEquals(appliedMigrations, None)
  }

  test("migrationNote: when artifact has migrations") {
    val update = Update.Single("com.spotify" % "scio-core" % "0.6.0", Nel.of("0.7.0"))
    val migration = ScalafixMigration(
      update.groupId,
      Nel.of(update.artifactId.name),
      Version("0.7.0"),
      Nel.of("I am a rewrite rule"),
      None,
      None
    )
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(List(migration))

    assertEquals(label, Some("scalafix-migrations"))
    assertEquals(
      appliedMigrations.fold("")(_.toHtml),
      """<details>
        |<summary>Migrations applied</summary>
        |
        |* I am a rewrite rule
        |</details>
      """.stripMargin.trim
    )
  }

  test("migrationNote: when artifact has migrations with docs") {
    val update = Update.Single("com.spotify" % "scio-core" % "0.6.0", Nel.of("0.7.0"))
    val migration = ScalafixMigration(
      update.groupId,
      Nel.of(update.artifactId.name),
      Version("0.7.0"),
      Nel.of("I am a rewrite rule"),
      Some("https://scalacenter.github.io/scalafix/"),
      None
    )
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(List(migration))

    assertEquals(label, Some("scalafix-migrations"))
    assertEquals(
      appliedMigrations.fold("")(_.toHtml),
      """<details>
        |<summary>Migrations applied</summary>
        |
        |* I am a rewrite rule
        |
        |Documentation:
        |
        |* https://scalacenter.github.io/scalafix/
        |</details>
      """.stripMargin.trim
    )
  }

  test("updateType") {
    val dependency = "com.example" % "foo" % "0.1"
    val single = Update.Single(dependency, Nel.of("0.2"))
    val group = Update.Group("com.example" % Nel.of("foo", "bar") % "0.1", Nel.of("0.2"))
    assertEquals(NewPullRequestData.updateType(single), "library-update")
    assertEquals(NewPullRequestData.updateType(group), "library-update")

    assertEquals(
      NewPullRequestData.updateType(Update.Single(dependency % "test", Nel.of("0.2"))),
      "test-library-update"
    )
    assertEquals(
      NewPullRequestData.updateType(
        Update.Single(dependency.copy(sbtVersion = Some(SbtVersion("1.0"))), Nel.of("0.2"))
      ),
      "sbt-plugin-update"
    )
    assertEquals(
      NewPullRequestData.updateType(
        Update.Single(dependency.copy(configurations = Some("scalafix-rule")), Nel.of("0.2"))
      ),
      "scalafix-rule-update"
    )
  }

  test("oldVersionNote without files") {
    val files = List.empty
    val update = Update.Single("com.example" % "foo" % "0.1", Nel.of("0.2"))

    assertEquals(NewPullRequestData.oldVersionNote(files, update), (None, None))
  }

  test("oldVersionNote with files") {
    val files = List("Readme.md", "travis.yml")
    val update = Update.Single("com.example" % "foo" % "0.1", Nel.of("0.2"))

    val (label, note) = NewPullRequestData.oldVersionNote(files, update)

    assertEquals(label, Some("old-version-remains"))
    assertEquals(
      note.fold("")(_.toHtml),
      """<details>
        |<summary>Files still referring to the old version number</summary>
        |
        |The following files still refer to the old version number (0.1).
        |You might want to review and update them manually.
        |```
        |Readme.md
        |travis.yml
        |```
        |</details>
      """.stripMargin.trim
    )
  }
}
