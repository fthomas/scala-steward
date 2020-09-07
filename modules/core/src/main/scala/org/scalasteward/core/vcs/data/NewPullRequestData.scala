/*
 * Copyright 2018-2020 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.vcs.data

import cats.syntax.all._
import io.circe.Encoder
import io.circe.generic.semiauto._
import org.http4s.Uri
import org.scalasteward.core.data.{GroupId, ReleaseRelatedUrl, SemVer, Update}
import org.scalasteward.core.git
import org.scalasteward.core.git.Branch
import org.scalasteward.core.nurture.UpdateData
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.scalafix.Migration
import org.scalasteward.core.util.{Details, Nel}

final case class NewPullRequestData(
    title: String,
    body: String,
    head: String,
    base: Branch
)

object NewPullRequestData {
  implicit val newPullRequestDataEncoder: Encoder[NewPullRequestData] =
    deriveEncoder

  def bodyFor(
      update: Update,
      artifactIdToUrl: Map[String, Uri],
      releaseRelatedUrls: List[ReleaseRelatedUrl],
      migrations: List[Migration]
  ): String = {
    val artifacts = artifactsWithOptionalUrl(update, artifactIdToUrl)
    val (migrationLabel, appliedMigrations) = migrationNote(migrations)
    val details = ignoreFutureUpdates(update) :: appliedMigrations.toList
    val labels =
      Nel.fromList(List(updateType(update)) ++ semVerLabel(update).toList ++ migrationLabel.toList)

    s"""|Updates $artifacts ${fromTo(update)}.
        |${releaseNote(releaseRelatedUrls).getOrElse("")}
        |
        |I'll automatically update this PR to resolve conflicts as long as you don't change it yourself.
        |
        |If you'd like to skip this version, you can just close this PR. If you have any feedback, just mention me in the comments below.
        |
        |Configure Scala Steward for your repository with a [`${RepoConfigAlg.repoConfigBasename}`](https://github.com/fthomas/scala-steward/blob/${org.scalasteward.core.BuildInfo.gitHeadCommit}/docs/repo-specific-configuration.md) file.
        |
        |Have a fantastic day writing Scala!
        |
        |${details.map(_.toHtml).mkString("\n")}
        |
        |${labels.fold("")(_.mkString_("labels: ", ", ", ""))}
        |""".stripMargin.trim
  }

  def updateType(update: Update): String = {
    val dependencies = update.dependencies
    if (dependencies.forall(_.configurations.contains("test")))
      "test-library-update"
    else if (dependencies.forall(_.configurations.contains("scalafix-rule")))
      "scalafix-rule-update"
    else if (dependencies.forall(_.sbtVersion.isDefined))
      "sbt-plugin-update"
    else
      "library-update"
  }

  def releaseNote(releaseRelatedUrls: List[ReleaseRelatedUrl]): Option[String] =
    if (releaseRelatedUrls.isEmpty) None
    else
      releaseRelatedUrls
        .map { url =>
          url match {
            case ReleaseRelatedUrl.CustomChangelog(url) => s"[Changelog](${url.renderString})"
            case ReleaseRelatedUrl.CustomReleaseNotes(url) =>
              s"[Release Notes](${url.renderString})"
            case ReleaseRelatedUrl.GitHubReleaseNotes(url) =>
              s"[GitHub Release Notes](${url.renderString})"
            case ReleaseRelatedUrl.VersionDiff(url) => s"[Version Diff](${url.renderString})"
          }
        }
        .mkString(" - ")
        .some

  def fromTo(update: Update): String =
    s"from ${update.currentVersion} to ${update.nextVersion}"

  def artifactsWithOptionalUrl(update: Update, artifactIdToUrl: Map[String, Uri]): String =
    update match {
      case s: Update.Single =>
        artifactWithOptionalUrl(s.groupId, s.artifactId.name, artifactIdToUrl)
      case g: Update.Group =>
        g.crossDependencies
          .map(crossDependency =>
            s"* ${artifactWithOptionalUrl(g.groupId, crossDependency.head.artifactId.name, artifactIdToUrl)}\n"
          )
          .mkString_("\n", "", "\n")
    }

  def artifactWithOptionalUrl(
      groupId: GroupId,
      artifactId: String,
      artifactId2Url: Map[String, Uri]
  ): String =
    artifactId2Url.get(artifactId) match {
      case Some(url) => s"[$groupId:$artifactId](${url.renderString})"
      case None      => s"$groupId:$artifactId"
    }

  def ignoreFutureUpdates(update: Update): Details =
    Details(
      "Ignore future updates",
      s"""|Add this to your `${RepoConfigAlg.repoConfigBasename}` file to ignore future updates of this dependency:
          |```
          |${RepoConfigAlg.configToIgnoreFurtherUpdates(update)}
          |```
          |""".stripMargin.trim
    )

  def migrationNote(migrations: List[Migration]): (Option[String], Option[Details]) =
    if (migrations.isEmpty) (None, None)
    else {
      val ruleList =
        migrations.flatMap(_.rewriteRules.toList).map(rule => s"* $rule").mkString("\n")
      val docList = migrations.flatMap(_.doc).map(uri => s"* $uri").mkString("\n")
      val docSection =
        if (docList.isEmpty)
          ""
        else
          s"\n\nDocumentation:\n\n$docList"
      (
        Some("scalafix-migrations"),
        Some(
          Details(
            "Applied Migrations",
            s"$ruleList$docSection"
          )
        )
      )
    }

  def semVerLabel(update: Update): Option[String] =
    for {
      curr <- SemVer.parse(update.currentVersion)
      next <- SemVer.parse(update.nextVersion)
      change <- SemVer.getChange(curr, next)
    } yield s"semver-${change.render}"

  def from(
      data: UpdateData,
      branchName: String,
      artifactIdToUrl: Map[String, Uri] = Map.empty,
      releaseRelatedUrls: List[ReleaseRelatedUrl] = List.empty,
      migrations: List[Migration] = List.empty,
      nonDefaultBaseBranch: Option[Branch] = None
  ): NewPullRequestData =
    NewPullRequestData(
      title = git.commitMsgFor(data.update, data.repoConfig.commits, nonDefaultBaseBranch),
      body = bodyFor(
        data.update,
        artifactIdToUrl,
        releaseRelatedUrls,
        migrations
      ),
      head = branchName,
      base = data.baseBranch
    )
}
