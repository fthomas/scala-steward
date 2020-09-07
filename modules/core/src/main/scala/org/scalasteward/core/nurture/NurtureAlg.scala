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

package org.scalasteward.core.nurture

import cats.effect.Sync
import cats.implicits._
import eu.timepit.refined.types.numeric.PosInt
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.ProcessResult.{Ignored, Updated}
import org.scalasteward.core.data.{ProcessResult, Scope, Update}
import org.scalasteward.core.edit.EditAlg
import org.scalasteward.core.git.{Branch, Commit, GitAlg}
import org.scalasteward.core.repocache.RepoCacheRepository
import org.scalasteward.core.repoconfig.{PullRequestUpdateStrategy, RepoConfigAlg}
import org.scalasteward.core.scalafix.MigrationAlg
import org.scalasteward.core.util.logger.LoggerOps
import org.scalasteward.core.util.{DateTimeAlg, HttpExistenceClient}
import org.scalasteward.core.vcs.data.{NewPullRequestData, Repo, RepoOut}
import org.scalasteward.core.vcs.{VCSApiAlg, VCSExtraAlg, VCSRepoAlg}
import org.scalasteward.core.{git, util, vcs}

final class NurtureAlg[F[_]](implicit
    config: Config,
    dateTimeAlg: DateTimeAlg[F],
    editAlg: EditAlg[F],
    repoConfigAlg: RepoConfigAlg[F],
    gitAlg: GitAlg[F],
    coursierAlg: CoursierAlg[F],
    vcsApiAlg: VCSApiAlg[F],
    vcsRepoAlg: VCSRepoAlg[F],
    vcsExtraAlg: VCSExtraAlg[F],
    existenceClient: HttpExistenceClient[F],
    logger: Logger[F],
    migrationAlg: MigrationAlg,
    pullRequestRepository: PullRequestRepository[F],
    repoCacheRepository: RepoCacheRepository[F],
    F: Sync[F]
) {
  def nurture(repo: Repo, updates: List[Update.Single]): F[Either[Throwable, Unit]] = {
    val label = s"Nurture ${repo.show}"
    logger.infoTotalTime(label) {
      logger.attemptLog(util.string.lineLeftRight(label)) {
        F.bracket(cloneAndSync(repo)) {
          case (fork, baseBranch) => updateDependencies(repo, fork, baseBranch, updates)
        }(_ => gitAlg.removeClone(repo))
      }
    }
  }

  def cloneAndSync(repo: Repo): F[(Repo, Branch)] =
    for {
      _ <- logger.info(s"Clone and synchronize ${repo.show}")
      repoOut <- vcsApiAlg.createForkOrGetRepo(config, repo)
      branch <- repo.branch.map(F.pure).getOrElse(vcsRepoAlg.defaultBranch(repoOut))
      _ <-
        gitAlg
          .cloneExists(repo)
          .ifM(
            F.unit,
            vcsRepoAlg.clone(repo, repoOut) >> vcsRepoAlg.syncFork(repo, repoOut) >> gitAlg
              .checkoutBranch(repo, branch)
          )
    } yield (repoOut.repo, branch)

  def updateDependencies(
      repo: Repo,
      fork: Repo,
      baseBranch: Branch,
      updates: List[Update.Single]
  ): F[Unit] =
    for {
      repoConfig <- repoConfigAlg.readRepoConfigOrDefault(repo)
      grouped = Update.groupByGroupId(updates)
      sorted = grouped.sortBy(migrationAlg.findMigrations(_).size)
      _ <- logger.info(util.logger.showUpdates(sorted))
      _ <- gitAlg.checkoutBranch(repo, baseBranch)
      baseSha1 <- gitAlg.latestSha1(repo, baseBranch)
      repoOut <- vcsApiAlg.getRepo(repo)
      nonDefaultBranch <- nonDefaultBranchOrNone(repoOut, baseBranch)
      _ <- NurtureAlg.processUpdates(
        sorted,
        update =>
          processUpdate(
            UpdateData(
              repo,
              fork,
              repoConfig,
              update,
              baseBranch,
              baseSha1,
              git.branchFor(update, nonDefaultBranch)
            )
          ),
        repoConfig.updates.limit
      )
    } yield ()

  def processUpdate(data: UpdateData): F[ProcessResult] =
    for {
      _ <- logger.info(s"Process update ${data.update.show}")
      repoOut <- vcsApiAlg.getRepo(data.repo)
      nonDefaultBranch <- nonDefaultBranchOrNone(repoOut, data.baseBranch)
      head = vcs.listingBranch(config.vcsType, data.fork, data.update, nonDefaultBranch)
      pullRequests <- vcsApiAlg.listPullRequests(data.repo, head, data.baseBranch)
      result <- pullRequests.headOption match {
        case Some(pr) if pr.isClosed =>
          logger.info(s"PR ${pr.html_url} is closed") >> F.pure[ProcessResult](Ignored)
        case Some(pr) =>
          logger.info(s"Found PR ${pr.html_url}") >> updatePullRequest(data)
        case None =>
          applyNewUpdate(data)
      }
      _ <- pullRequests.headOption.traverse_ { pr =>
        pullRequestRepository.createOrUpdate(
          data.repo,
          pr.html_url,
          data.baseSha1,
          data.update,
          pr.state
        )
      }
    } yield result

  def applyNewUpdate(data: UpdateData): F[ProcessResult] =
    (editAlg.applyUpdate(
      data.repo,
      data.update,
      data.repoConfig.updates.fileExtensionsOrDefault
    ) >> gitAlg.containsChanges(data.repo)).ifM(
      gitAlg.returnToCurrentBranch(data.repo) {
        for {
          _ <- logger.info(s"Create branch ${data.updateBranch.name}")
          _ <- gitAlg.createBranch(data.repo, data.updateBranch)
          maybeCommit <- commitChanges(data)
          _ <- pushCommits(data, maybeCommit.toList)
          _ <- createPullRequest(data)
        } yield Updated
      },
      logger.warn("No files were changed") >> F.pure[ProcessResult](Ignored)
    )

  def commitChanges(data: UpdateData): F[Option[Commit]] =
    for {
      repoOut <- vcsApiAlg.getRepo(data.repo)
      nonDefaultBranch <- nonDefaultBranchOrNone(repoOut, data.baseBranch)
      commit <- gitAlg.commitAllIfDirty(
        data.repo,
        git.commitMsgFor(data.update, data.repoConfig.commits, nonDefaultBranch)
      )
    } yield commit

  def pushCommits(data: UpdateData, commits: List[Commit]): F[ProcessResult] =
    if (commits.isEmpty) F.pure[ProcessResult](Ignored)
    else
      for {
        _ <- logger.info(s"Push ${commits.length} commit(s)")
        _ <- gitAlg.push(data.repo, data.updateBranch)
      } yield Updated

  private def nonDefaultBranchOrNone(repoOut: RepoOut, baseBranch: Branch): F[Option[Branch]] =
    vcsRepoAlg
      .defaultBranch(repoOut)
      .map(branch => if (branch.name === baseBranch.name) None else Some(branch))

  def createPullRequest(data: UpdateData): F[Unit] =
    for {
      _ <- logger.info(s"Create PR ${data.updateBranch.name}")
      maybeRepoCache <- repoCacheRepository.findCache(data.repo)
      resolvers = maybeRepoCache.map(_.dependencyInfos.flatMap(_.resolvers)).getOrElse(List.empty)
      artifactIdToUrl <- coursierAlg.getArtifactIdUrlMapping(
        Scope(data.update.dependencies.toList, resolvers)
      )
      existingArtifactUrlsList <- artifactIdToUrl.toList.filterA(a => existenceClient.exists(a._2))
      existingArtifactUrlsMap = existingArtifactUrlsList.toMap
      releaseRelatedUrls <-
        existingArtifactUrlsMap
          .get(data.update.mainArtifactId)
          .traverse(vcsExtraAlg.getReleaseRelatedUrls(_, data.update))
      repoOut <- vcsApiAlg.getRepo(data.repo)
      nonDefaultBaseBranch <- nonDefaultBranchOrNone(repoOut, data.baseBranch)
      branchName = vcs.createBranch(config.vcsType, data.fork, data.update, nonDefaultBaseBranch)
      migrations = migrationAlg.findMigrations(data.update)
      requestData = NewPullRequestData.from(
        data,
        branchName,
        existingArtifactUrlsMap,
        releaseRelatedUrls.getOrElse(List.empty),
        migrations,
        nonDefaultBaseBranch
      )
      pr <- vcsApiAlg.createPullRequest(data.repo, requestData)
      _ <- pullRequestRepository.createOrUpdate(
        data.repo,
        pr.html_url,
        data.baseSha1,
        data.update,
        pr.state
      )
      _ <- logger.info(s"Created PR ${pr.html_url}")
    } yield ()

  def updatePullRequest(data: UpdateData): F[ProcessResult] =
    if (data.repoConfig.updatePullRequestsOrDefault =!= PullRequestUpdateStrategy.Never)
      gitAlg.returnToCurrentBranch(data.repo) {
        for {
          _ <- gitAlg.checkoutBranch(data.repo, data.updateBranch)
          update <- shouldBeUpdated(data)
          result <- if (update) mergeAndApplyAgain(data) else F.pure[ProcessResult](Ignored)
        } yield result
      }
    else
      logger.info("PR updates are disabled by flag").as(Ignored)

  def shouldBeUpdated(data: UpdateData): F[Boolean] = {
    val result = gitAlg.isMerged(data.repo, data.updateBranch, data.baseBranch).flatMap {
      case true => (false, "PR has been merged").pure[F]
      case false =>
        gitAlg.branchAuthors(data.repo, data.updateBranch, data.baseBranch).flatMap { authors =>
          val distinctAuthors = authors.distinct
          if (distinctAuthors.length >= 2)
            (false, s"PR has commits by ${distinctAuthors.mkString(", ")}").pure[F]
          else if (data.repoConfig.updatePullRequestsOrDefault === PullRequestUpdateStrategy.Always)
            (true, "PR update strategy is set to always").pure[F]
          else
            gitAlg.hasConflicts(data.repo, data.updateBranch, data.baseBranch).map {
              case true  => (true, s"PR has conflicts with ${data.baseBranch.name}")
              case false => (false, s"PR has no conflict with ${data.baseBranch.name}")
            }
        }
    }
    result.flatMap { case (update, msg) => logger.info(msg).as(update) }
  }

  def mergeAndApplyAgain(data: UpdateData): F[ProcessResult] =
    for {
      _ <- logger.info(
        s"Merge branch '${data.baseBranch.name}' into ${data.updateBranch.name} and apply again"
      )
      maybeMergeCommit <- gitAlg.mergeTheirs(data.repo, data.baseBranch)
      _ <- editAlg.applyUpdate(
        data.repo,
        data.update,
        data.repoConfig.updates.fileExtensionsOrDefault
      )
      maybeCommit <- commitChanges(data)
      result <- pushCommits(data, maybeMergeCommit.toList ++ maybeCommit.toList)
    } yield result
}

object NurtureAlg {
  def processUpdates[F[_]: Sync](
      updates: List[Update],
      updateF: Update => F[ProcessResult],
      updatesLimit: Option[PosInt]
  ): F[Unit] =
    updatesLimit match {
      case None => updates.traverse_(updateF)
      case Some(limit) =>
        fs2.Stream
          .emits(updates)
          .evalMap(updateF)
          .through(util.takeUntil(limit.value) {
            case Ignored => 0
            case Updated => 1
          })
          .compile
          .drain
    }
}
