package org.scalasteward.core.vcs.data

import io.circe.syntax._
import org.http4s.syntax.literals._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.buildtool.sbt.data.SbtVersion
import org.scalasteward.core.data.{ReleaseRelatedUrl, Update, Version}
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.nurture.UpdateData
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.Nel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class NewPullRequestDataTest extends AnyFunSuite with Matchers {
  test("asJson") {
    val data = UpdateData(
      Repo("foo", "bar"),
      Repo("scala-steward", "bar"),
      RepoConfig(),
      Update.Single("ch.qos.logback" % "logback-classic" % "1.2.0", Nel.of("1.2.3")),
      Branch("master"),
      Sha1(Sha1.HexString("d6b6791d2ea11df1d156fe70979ab8c3a5ba3433")),
      Branch("update/logback-classic-1.2.3")
    )

    NewPullRequestData
      .from(data, "scala-steward:update/logback-classic-1.2.3")
      .asJson
      .spaces2 shouldBe
      raw"""|{
            |  "title" : "Update logback-classic to 1.2.3",
            |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\n\nI'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n\nIf you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.\n\nConfigure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/fthomas/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.\n\nHave a fantastic day writing Scala!\n\n<details>\n<summary>Ignore future updates</summary>\n\nAdd this to your `.scala-steward.conf` file to ignore future updates of this dependency:\n```\nupdates.ignore = [ { groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" } ]\n```\n</details>\n\nlabels: library-update, semver-patch",
            |  "head" : "scala-steward:update/logback-classic-1.2.3",
            |  "base" : "master"
            |}""".stripMargin

    val dataWithNonDefaultBranch = data.copy(
      baseBranch = Branch("non-default"),
      updateBranch = Branch("update/non-default/logback-classic-1.2.3")
    )

    NewPullRequestData
      .from(
        data,
        "scala-steward:update/non-default/logback-classic-1.2.3",
        nonDefaultBaseBranch = Some(dataWithNonDefaultBranch.baseBranch)
      )
      .asJson
      .spaces2 shouldBe
      raw"""|{
            |  "title" : "Update logback-classic to 1.2.3 (non-default)",
            |  "body" : "Updates ch.qos.logback:logback-classic from 1.2.0 to 1.2.3.\n\n\nI'll automatically update this PR to resolve conflicts as long as you don't change it yourself.\n\nIf you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.\n\nConfigure Scala Steward for your repository with a [`.scala-steward.conf`](https://github.com/fthomas/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.\n\nHave a fantastic day writing Scala!\n\n<details>\n<summary>Ignore future updates</summary>\n\nAdd this to your `.scala-steward.conf` file to ignore future updates of this dependency:\n```\nupdates.ignore = [ { groupId = \"ch.qos.logback\", artifactId = \"logback-classic\" } ]\n```\n</details>\n\nlabels: library-update, semver-patch",
            |  "head" : "scala-steward:update/non-default/logback-classic-1.2.3",
            |  "base" : "master"
            |}""".stripMargin
  }

  test("fromTo") {
    NewPullRequestData.fromTo(
      Update.Single("com.example" % "foo" % "1.2.0", Nel.of("1.2.3"))
    ) shouldBe "from 1.2.0 to 1.2.3"
  }

  test("links to release notes/changelog") {
    NewPullRequestData.releaseNote(List.empty) shouldBe None

    NewPullRequestData.releaseNote(
      List(ReleaseRelatedUrl.CustomChangelog(uri"https://github.com/foo/foo/CHANGELOG.rst"))
    ) shouldBe Some(
      "[Changelog](https://github.com/foo/foo/CHANGELOG.rst)"
    )

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
    ) shouldBe Some(
      "[Changelog](https://github.com/foo/foo/CHANGELOG.rst) - [GitHub Release Notes](https://github.com/foo/foo/releases/tag/v1.2.3) - [Release Notes](https://github.com/foo/bar/blob/master/ReleaseNotes.md) - [Release Notes](https://github.com/foo/bar/blob/master/Releases.md) - [Version Diff](https://github.com/foo/foo/compare/v1.2.0...v1.2.3)"
    )
  }

  test("showing artifacts with URL in Markdown format") {
    NewPullRequestData.artifactsWithOptionalUrl(
      Update.Single("com.example" % "foo" % "1.2.0", Nel.of("1.2.3")),
      Map("foo" -> uri"https://github.com/foo/foo")
    ) shouldBe "[com.example:foo](https://github.com/foo/foo)"

    NewPullRequestData.artifactsWithOptionalUrl(
      Update.Group("com.example" % Nel.of("foo", "bar") % "1.2.0", Nel.of("1.2.3")),
      Map("foo" -> uri"https://github.com/foo/foo", "bar" -> uri"https://github.com/bar/bar")
    ) shouldBe
      """
        |* [com.example:foo](https://github.com/foo/foo)
        |* [com.example:bar](https://github.com/bar/bar)
        |
        |""".stripMargin
  }

  test("migrationNote: when no migrations") {
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(List.empty)

    label shouldBe None
    appliedMigrations shouldBe None
  }

  test("migrationNote: when artifact has migrations") {
    val update = Update.Single("com.spotify" % "scio-core" % "0.6.0", Nel.of("0.7.0"))
    val migration = Migration(
      update.groupId,
      Nel.of(update.artifactId.name),
      Version("0.7.0"),
      Nel.of("I am a rewrite rule"),
      None,
      None
    )
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(List(migration))

    label shouldBe Some("scalafix-migrations")
    appliedMigrations.fold("")(_.toHtml) shouldBe
      """<details>
        |<summary>Applied Migrations</summary>
        |
        |* I am a rewrite rule
        |</details>
      """.stripMargin.trim
  }

  test("migrationNote: when artifact has migrations with docs") {
    val update = Update.Single("com.spotify" % "scio-core" % "0.6.0", Nel.of("0.7.0"))
    val migration = Migration(
      update.groupId,
      Nel.of(update.artifactId.name),
      Version("0.7.0"),
      Nel.of("I am a rewrite rule"),
      Some("https://scalacenter.github.io/scalafix/"),
      None
    )
    val (label, appliedMigrations) = NewPullRequestData.migrationNote(List(migration))

    label shouldBe Some("scalafix-migrations")
    appliedMigrations.fold("")(_.toHtml) shouldBe
      """<details>
        |<summary>Applied Migrations</summary>
        |
        |* I am a rewrite rule
        |
        |Documentation:
        |
        |* https://scalacenter.github.io/scalafix/
        |</details>
      """.stripMargin.trim
  }

  test("updateType") {
    val dependency = "com.example" % "foo" % "0.1"
    val single = Update.Single(dependency, Nel.of("0.2"))
    val group = Update.Group("com.example" % Nel.of("foo", "bar") % "0.1", Nel.of("0.2"))
    NewPullRequestData.updateType(single) shouldBe "library-update"
    NewPullRequestData.updateType(group) shouldBe "library-update"

    NewPullRequestData.updateType(
      Update.Single(dependency % "test", Nel.of("0.2"))
    ) shouldBe "test-library-update"
    NewPullRequestData.updateType(
      Update.Single(dependency.copy(sbtVersion = Some(SbtVersion("1.0"))), Nel.of("0.2"))
    ) shouldBe "sbt-plugin-update"
    NewPullRequestData.updateType(
      Update.Single(dependency.copy(configurations = Some("scalafix-rule")), Nel.of("0.2"))
    ) shouldBe "scalafix-rule-update"
  }
}
