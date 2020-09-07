package org.scalasteward.core.vcs

import org.http4s.syntax.literals._
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.application.SupportedVCS.{GitHub, Gitlab}
import org.scalasteward.core.data.Update
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.Repo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class VCSPackageTest extends AnyFunSuite with Matchers {
  val repo: Repo = Repo("foo", "bar")
  val update: Update.Single =
    Update.Single("ch.qos.logback" % "logback-classic" % "1.2.0", Nel.of("1.2.3"))

  test("listingBranch") {
    listingBranch(
      GitHub,
      repo,
      update,
      Some(Branch("non-default"))
    ) shouldBe "foo/bar:update/non-default/logback-classic-1.2.3"
    listingBranch(
      Gitlab,
      repo,
      update,
      Some(Branch("non-default"))
    ) shouldBe "update/non-default/logback-classic-1.2.3"

    listingBranch(
      GitHub,
      repo,
      update
    ) shouldBe "foo/bar:update/logback-classic-1.2.3"
    listingBranch(
      Gitlab,
      repo,
      update
    ) shouldBe "update/logback-classic-1.2.3"
  }

  test("createBranch") {
    createBranch(
      GitHub,
      repo,
      update,
      Some(Branch("non-default"))
    ) shouldBe "foo:update/non-default/logback-classic-1.2.3"
    createBranch(
      Gitlab,
      repo,
      update,
      Some(Branch("non-default"))
    ) shouldBe "update/non-default/logback-classic-1.2.3"

    createBranch(
      GitHub,
      repo,
      update
    ) shouldBe "foo:update/logback-classic-1.2.3"
    createBranch(
      Gitlab,
      repo,
      update
    ) shouldBe "update/logback-classic-1.2.3"
  }

  test("possibleCompareUrls") {
    possibleCompareUrls(uri"https://github.com/foo/bar", update)
      .map(_.url.renderString) shouldBe List(
      "https://github.com/foo/bar/compare/v1.2.0...v1.2.3",
      "https://github.com/foo/bar/compare/1.2.0...1.2.3",
      "https://github.com/foo/bar/compare/release-1.2.0...release-1.2.3"
    )
    // should canonicalize (drop last slash)
    possibleCompareUrls(uri"https://github.com/foo/bar/", update)
      .map(_.url.renderString) shouldBe List(
      "https://github.com/foo/bar/compare/v1.2.0...v1.2.3",
      "https://github.com/foo/bar/compare/1.2.0...1.2.3",
      "https://github.com/foo/bar/compare/release-1.2.0...release-1.2.3"
    )

    possibleCompareUrls(uri"https://gitlab.com/foo/bar", update)
      .map(_.url.renderString) shouldBe List(
      "https://gitlab.com/foo/bar/compare/v1.2.0...v1.2.3",
      "https://gitlab.com/foo/bar/compare/1.2.0...1.2.3",
      "https://gitlab.com/foo/bar/compare/release-1.2.0...release-1.2.3"
    )
    possibleCompareUrls(uri"https://bitbucket.org/foo/bar", update)
      .map(_.url.renderString) shouldBe List(
      "https://bitbucket.org/foo/bar/compare/v1.2.3..v1.2.0#diff",
      "https://bitbucket.org/foo/bar/compare/1.2.3..1.2.0#diff",
      "https://bitbucket.org/foo/bar/compare/release-1.2.3..release-1.2.0#diff"
    )

    possibleCompareUrls(uri"https://scalacenter.github.io/scalafix/", update) shouldBe List()
  }

  test("possibleChangelogUrls: github.com") {
    possibleReleaseRelatedUrls(uri"https://github.com/foo/bar", update)
      .map(_.url.renderString) shouldBe List(
      "https://github.com/foo/bar/releases/tag/v1.2.3",
      "https://github.com/foo/bar/releases/tag/1.2.3",
      "https://github.com/foo/bar/releases/tag/release-1.2.3",
      "https://github.com/foo/bar/blob/master/ReleaseNotes.md",
      "https://github.com/foo/bar/blob/master/ReleaseNotes.markdown",
      "https://github.com/foo/bar/blob/master/ReleaseNotes.rst",
      "https://github.com/foo/bar/blob/master/RELEASES.md",
      "https://github.com/foo/bar/blob/master/RELEASES.markdown",
      "https://github.com/foo/bar/blob/master/RELEASES.rst",
      "https://github.com/foo/bar/blob/master/Releases.md",
      "https://github.com/foo/bar/blob/master/Releases.markdown",
      "https://github.com/foo/bar/blob/master/Releases.rst",
      "https://github.com/foo/bar/blob/master/releases.md",
      "https://github.com/foo/bar/blob/master/releases.markdown",
      "https://github.com/foo/bar/blob/master/releases.rst",
      "https://github.com/foo/bar/blob/master/CHANGELOG.md",
      "https://github.com/foo/bar/blob/master/CHANGELOG.markdown",
      "https://github.com/foo/bar/blob/master/CHANGELOG.rst",
      "https://github.com/foo/bar/blob/master/Changelog.md",
      "https://github.com/foo/bar/blob/master/Changelog.markdown",
      "https://github.com/foo/bar/blob/master/Changelog.rst",
      "https://github.com/foo/bar/blob/master/changelog.md",
      "https://github.com/foo/bar/blob/master/changelog.markdown",
      "https://github.com/foo/bar/blob/master/changelog.rst",
      "https://github.com/foo/bar/blob/master/CHANGES.md",
      "https://github.com/foo/bar/blob/master/CHANGES.markdown",
      "https://github.com/foo/bar/blob/master/CHANGES.rst",
      "https://github.com/foo/bar/compare/v1.2.0...v1.2.3",
      "https://github.com/foo/bar/compare/1.2.0...1.2.3",
      "https://github.com/foo/bar/compare/release-1.2.0...release-1.2.3"
    )
  }

  test("possibleChangelogUrls: gitlab.com") {
    possibleReleaseRelatedUrls(uri"https://gitlab.com/foo/bar", update)
      .map(_.url.renderString) shouldBe
      possibleReleaseNotesFilenames.map(name => s"https://gitlab.com/foo/bar/blob/master/$name") ++
        possibleChangelogFilenames.map(name => s"https://gitlab.com/foo/bar/blob/master/$name") ++
        List(
          "https://gitlab.com/foo/bar/compare/v1.2.0...v1.2.3",
          "https://gitlab.com/foo/bar/compare/1.2.0...1.2.3",
          "https://gitlab.com/foo/bar/compare/release-1.2.0...release-1.2.3"
        )
  }

  test("possibleChangelogUrls: bitbucket.org") {
    possibleReleaseRelatedUrls(uri"https://bitbucket.org/foo/bar", update)
      .map(_.url.renderString) shouldBe
      possibleReleaseNotesFilenames.map(name => s"https://bitbucket.org/foo/bar/master/$name") ++
        possibleChangelogFilenames.map(name => s"https://bitbucket.org/foo/bar/master/$name") ++
        List(
          "https://bitbucket.org/foo/bar/compare/v1.2.3..v1.2.0#diff",
          "https://bitbucket.org/foo/bar/compare/1.2.3..1.2.0#diff",
          "https://bitbucket.org/foo/bar/compare/release-1.2.3..release-1.2.0#diff"
        )
  }

  test("possibleChangelogUrls: homepage") {
    possibleReleaseRelatedUrls(uri"https://scalacenter.github.io/scalafix/", update)
      .map(_.url.renderString) shouldBe List()
  }
}
