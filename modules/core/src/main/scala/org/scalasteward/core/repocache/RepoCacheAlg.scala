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

package org.scalasteward.core.repocache

import cats.Parallel
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.data.{Dependency, DependencyInfo}
import org.scalasteward.core.git.{Branch, GitAlg}
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.util.MonadThrowable
import org.scalasteward.core.util.logger.LoggerOps
import org.scalasteward.core.vcs.data.{Repo, RepoOut}
import org.scalasteward.core.vcs.{VCSApiAlg, VCSRepoAlg}

final class RepoCacheAlg[F[_]](implicit
    buildToolDispatcher: BuildToolDispatcher[F],
    config: Config,
    gitAlg: GitAlg[F],
    logger: Logger[F],
    parallel: Parallel[F],
    refreshErrorAlg: RefreshErrorAlg[F],
    repoCacheRepository: RepoCacheRepository[F],
    repoConfigAlg: RepoConfigAlg[F],
    vcsApiAlg: VCSApiAlg[F],
    vcsRepoAlg: VCSRepoAlg[F],
    F: MonadThrowable[F]
) {
  def checkCache(repo: Repo): F[Unit] =
    logger.attemptLog_(s"Check cache of ${repo.show}") {
      F.ifM(refreshErrorAlg.failedRecently(repo))(
        logger.info(s"Skipping due to previous error"),
        for {
          ((repoOut, branchOut), cachedSha1) <- (
              vcsApiAlg.createForkOrGetRepoWithBranch(config, repo),
              repoCacheRepository.findSha1(repo)
          ).parTupled
          latestSha1 = branchOut.commit.sha
          refreshRequired = cachedSha1.forall(_ =!= latestSha1)
          _ <- if (refreshRequired) cloneAndRefreshCache(repo, repoOut) else F.unit
        } yield ()
      )
    }

  private def cloneAndRefreshCache(repo: Repo, repoOut: RepoOut): F[Unit] =
    for {
      _ <- logger.info(s"Refresh cache of ${repo.show}")
      defaultBranch <- vcsRepoAlg.defaultBranch(repoOut)
      _ <- vcsRepoAlg.clone(repo, repoOut)
      _ <- vcsRepoAlg.syncFork(repo, repoOut)
      _ <- refreshCache(repo, defaultBranch)
    } yield ()

  private def refreshCache(repo: Repo, defaultBranch: Branch): F[Unit] =
    computeCache(repo, defaultBranch).attempt.flatMap {
      case Right(cache) =>
        repoCacheRepository.updateCache(repo, cache)
      case Left(throwable) =>
        refreshErrorAlg.persistError(repo, throwable) >> F.raiseError(throwable)
    }

  private def computeCache(repo: Repo, defaultBranch: Branch): F[RepoCache] =
    for {
      _ <- gitAlg.checkoutBranch(repo, repo.branch.getOrElse(defaultBranch))
      branch <- gitAlg.currentBranch(repo)
      latestSha1 <- gitAlg.latestSha1(repo, branch)
      dependencies <- buildToolDispatcher.getDependencies(repo)
      dependencyInfos <-
        dependencies
          .traverse(_.traverse(_.traverse(gatherDependencyInfo(repo, _))))
      maybeRepoConfig <- repoConfigAlg.readRepoConfig(repo)
    } yield RepoCache(latestSha1, dependencyInfos, maybeRepoConfig)

  private def gatherDependencyInfo(repo: Repo, dependency: Dependency): F[DependencyInfo] =
    gitAlg.findFilesContaining(repo, dependency.version).map(DependencyInfo(dependency, _))
}
