/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.core.nurture

import better.files.File
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.data.Update
import org.scalasteward.core.edit.EditAlg
import org.scalasteward.core.git.{Branch, GitAlg}
import org.scalasteward.core.io.WorkspaceAlg
import org.scalasteward.core.repoconfig.{RepoConfig, RepoConfigAlg}
import org.scalasteward.core.sbt.SbtAlg
import org.scalasteward.core.update.FilterAlg
import org.scalasteward.core.util.{BracketThrowable, LogAlg}
import org.scalasteward.core.vcs.data.{NewPullRequestData, Repo}
import org.scalasteward.core.vcs.{VCSApiAlg, VCSRepoAlg}
import org.scalasteward.core.{git, util, vcs}

final class NurtureAlg[F[_]](
    implicit
    config: Config,
    editAlg: EditAlg[F],
    repoConfigAlg: RepoConfigAlg[F],
    filterAlg: FilterAlg[F],
    gitAlg: GitAlg[F],
    vcsApiAlg: VCSApiAlg[F],
    vcsRepoAlg: VCSRepoAlg[F],
    logAlg: LogAlg[F],
    logger: Logger[F],
    pullRequestRepo: PullRequestRepository[F],
    sbtAlg: SbtAlg[F],
    workspaceAlg: WorkspaceAlg[F],
    F: BracketThrowable[F]
) {
  def nurture(repo: Repo): F[Unit] =
    logAlg.infoTotalTime(repo.show) {
      logAlg.attemptLog_(util.string.lineLeftRight(s"Nurture ${repo.show}")) {
        for {
          (fork, baseBranch) <- cloneAndSync(repo)
          _ <- updateDependencies(repo, fork, baseBranch)
          _ <- gitAlg.removeClone(repo)
        } yield ()
      }
    }

  def cloneAndSync(repo: Repo): F[(Repo, Branch)] =
    for {
      _ <- logger.info(s"Clone and synchronize ${repo.show}")
      repoOut <- vcsApiAlg.createForkOrGetRepo(config, repo)
      _ <- vcsRepoAlg.clone(repo, repoOut)
      parent <- vcsRepoAlg.syncFork(repo, repoOut)
    } yield (repoOut.repo, parent.default_branch)

  def updateDependencies(repo: Repo, fork: Repo, baseBranch: Branch): F[Unit] =
    for {
      _ <- logger.info(s"Find updates for ${repo.show}")
      repoConfig <- repoConfigAlg.readRepoConfigOrDefault(repo)
      subProjects <- workspaceAlg.findSubProjectDirs(repo)
      updates <- findUpdatesInAllProjects(subProjects, repoConfig)
      grouped = Update.group(updates)
      _ <- logger.info(util.logger.showUpdates(grouped))
      baseSha1 <- gitAlg.latestSha1(repo, baseBranch)
      _ <- grouped.traverse_ { update =>
        val data =
          UpdateData(repo, fork, repoConfig, update, baseBranch, baseSha1, git.branchFor(update))
        processUpdate(data)
      }
    } yield ()

  def findUpdatesInAllProjects(
      subProjectRoots: List[File],
      repoConfig: RepoConfig
  ): F[List[Update.Single]] =
    subProjectRoots
      .traverse { projectDir =>
        for {
          updates <- sbtAlg.getUpdatesForRepo(projectDir)
          filtered <- filterAlg.localFilterMany(repoConfig, updates)
        } yield filtered
      }
      .map(_.flatten)

  def processUpdate(data: UpdateData): F[Unit] =
    for {
      _ <- logger.info(s"Process update ${data.update.show}")
      head = vcs.listingBranch(config.vcsType, data.fork, data.update)
      pullRequests <- vcsApiAlg.listPullRequests(data.repo, head, data.baseBranch)
      _ <- pullRequests.headOption match {
        case Some(pr) if pr.isClosed =>
          logger.info(s"PR ${pr.html_url} is closed")
        case Some(pr) if data.repoConfig.updatePullRequests =>
          logger.info(s"Found PR ${pr.html_url}") >> updatePullRequest(data)
        case Some(pr) =>
          logger.info(s"Found PR ${pr.html_url}, but updates are disabled by flag")
        case None =>
          applyNewUpdate(data)
      }
      _ <- pullRequests.headOption.fold(F.unit) { pr =>
        pullRequestRepo.createOrUpdate(data.repo, pr.html_url, data.baseSha1, data.update, pr.state)
      }
    } yield ()

  def applyNewUpdate(data: UpdateData): F[Unit] =
    (editAlg.applyUpdate(data.repo, data.update) >> gitAlg.containsChanges(data.repo)).ifM(
      gitAlg.returnToCurrentBranch(data.repo) {
        for {
          _ <- logger.info(s"Create branch ${data.updateBranch.name}")
          _ <- gitAlg.createBranch(data.repo, data.updateBranch)
          _ <- commitAndPush(data)
          _ <- createPullRequest(data)
        } yield ()
      },
      logger.warn("No files were changed")
    )

  def commitAndPush(data: UpdateData): F[Unit] =
    for {
      _ <- gitAlg.commitAll(data.repo, git.commitMsgFor(data.update))
      _ <- gitAlg.push(data.repo, data.updateBranch)
    } yield ()

  def createPullRequest(data: UpdateData): F[Unit] =
    for {
      _ <- logger.info(s"Create PR ${data.updateBranch.name}")
      branchName = vcs.createBranch(config.vcsType, data.fork, data.update)
      requestData = NewPullRequestData.from(data, branchName, config.vcsLogin)
      pr <- vcsApiAlg.createPullRequest(data.repo, requestData)
      _ <- pullRequestRepo.createOrUpdate(
        data.repo,
        pr.html_url,
        data.baseSha1,
        data.update,
        pr.state
      )
      _ <- logger.info(s"Created PR ${pr.html_url}")
    } yield ()

  def updatePullRequest(data: UpdateData): F[Unit] =
    gitAlg.returnToCurrentBranch(data.repo) {
      for {
        _ <- gitAlg.checkoutBranch(data.repo, data.updateBranch)
        reset <- shouldBeReset(data)
        _ <- if (reset) resetAndUpdate(data) else F.unit
      } yield ()
    }

  def shouldBeReset(data: UpdateData): F[Boolean] =
    for {
      authors <- gitAlg.branchAuthors(data.repo, data.updateBranch, data.baseBranch)
      distinctAuthors = authors.distinct
      isBehind <- gitAlg.isBehind(data.repo, data.updateBranch, data.baseBranch)
      isMerged <- gitAlg.isMerged(data.repo, data.updateBranch, data.baseBranch)
      (result, msg) = {
        if (isMerged)
          (false, "PR has been merged")
        else if (distinctAuthors.length >= 2)
          (false, s"PR has commits by ${distinctAuthors.mkString(", ")}")
        else if (authors.length >= 2)
          (true, "PR has multiple commits")
        else if (isBehind)
          (true, s"PR is behind ${data.baseBranch.name}")
        else
          (false, s"PR is up-to-date with ${data.baseBranch.name}")
      }
      _ <- logger.info(msg)
    } yield result

  def resetAndUpdate(data: UpdateData): F[Unit] =
    for {
      _ <- logger.info(s"Reset and update ${data.updateBranch.name}")
      _ <- gitAlg.resetHard(data.repo, data.baseBranch)
      _ <- editAlg.applyUpdate(data.repo, data.update)
      containsChanges <- gitAlg.containsChanges(data.repo)
      _ <- if (containsChanges) commitAndPush(data) else F.unit
    } yield ()
}
