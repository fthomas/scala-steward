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

package org.scalasteward.core.update

import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.data.{Dependency, Update}
import org.scalasteward.core.nurture.PullRequestRepository
import org.scalasteward.core.repocache.{RepoCache, RepoCacheRepository}
import org.scalasteward.core.sbt._
import org.scalasteward.core.sbt.data.ArtificialProject
import org.scalasteward.core.update.data.UpdateState
import org.scalasteward.core.update.data.UpdateState._
import org.scalasteward.core.util.MonadThrowable
import org.scalasteward.core.vcs.data.PullRequestState.Closed
import org.scalasteward.core.vcs.data.Repo
import org.scalasteward.core.util

final class UpdateService[F[_]](
    implicit
    filterAlg: FilterAlg[F],
    logger: Logger[F],
    pullRequestRepo: PullRequestRepository[F],
    repoCacheRepository: RepoCacheRepository[F],
    sbtAlg: SbtAlg[F],
    updateRepository: UpdateRepository[F],
    F: MonadThrowable[F]
) {

  // WIP
  def checkForUpdates(repos: List[Repo]): F[List[Update.Single]] =
    updateRepository.deleteAll >>
      repoCacheRepository.getDependencies(repos).flatMap { dependencies =>
        val (libraries, plugins) = dependencies
          .filter(
            d =>
              FilterAlg.isIgnoredGlobally(d.toUpdate).isRight && UpdateService
                .includeInUpdateCheck(d)
          )
          .partition(_.sbtVersion.isEmpty)
        val libProjects = splitter
          .xxx(libraries)
          .map { libs =>
            ArtificialProject(
              defaultScalaVersion,
              defaultSbtVersion,
              libs.sortBy(_.formatAsModuleId),
              List.empty
            )
          }

        val pluginProjects = plugins
          .groupBy(_.sbtVersion)
          .flatMap {
            case (maybeSbtVersion, plugins1) =>
              splitter.xxx(plugins1).map { ps =>
                ArtificialProject(
                  defaultScalaVersion,
                  seriesToSpecificVersion(maybeSbtVersion.get),
                  List.empty,
                  ps.sortBy(_.formatAsModuleId)
                )
              }
          }
          .toList

        val x = (libProjects ++ pluginProjects).flatTraverse { prj =>
          val fa =
            util.divideOnError(prj)(sbtAlg.getUpdatesForProject)(_.halve.toList.flatMap {
              case (p1, p2) => List(p1, p2)
            }) { (failedP: ArtificialProject, t: Throwable) =>
              println(s"failed finding updates for $failedP")
              println(t)
              F.pure(List.empty[Update.Single])
            }

          fa.flatTap { updates =>
            logger.info(util.logger.showUpdates(updates.widen[Update])) >>
              updateRepository.saveMany(updates)
          }
        }

        x.flatMap(updates => filterAlg.globalFilterMany(updates))
      }

  def filterByApplicableUpdates(repos: List[Repo], updates: List[Update.Single]): F[List[Repo]] =
    repos.filterA(needsAttention(_, updates))

  def needsAttention(repo: Repo, updates: List[Update.Single]): F[Boolean] =
    for {
      allStates <- findAllUpdateStates(repo, updates)
      outdatedStates = allStates.filter {
        case DependencyOutdated(_, _)     => true
        case PullRequestOutdated(_, _, _) => true
        case _                            => false
      }
      isOutdated = outdatedStates.nonEmpty
      _ <- {
        if (isOutdated) {
          val statesAsString = util.string.indentLines(outdatedStates.map(_.toString).sorted)
          logger.info(s"Update states for ${repo.show}:\n" + statesAsString)
        } else F.unit
      }
    } yield isOutdated

  def findAllUpdateStates(repo: Repo, updates: List[Update.Single]): F[List[UpdateState]] =
    repoCacheRepository.findCache(repo).flatMap {
      case Some(repoCache) =>
        val dependencies = repoCache.dependencies
        dependencies.traverse { dependency =>
          findUpdateState(repo, repoCache, dependency, updates)
        }
      case None => List.empty[UpdateState].pure[F]
    }

  def findUpdateState(
      repo: Repo,
      repoCache: RepoCache,
      dependency: Dependency,
      updates: List[Update.Single]
  ): F[UpdateState] =
    updates.find(UpdateService.isUpdateFor(_, dependency)) match {
      case None => F.pure(DependencyUpToDate(dependency))
      case Some(update) =>
        repoCache.maybeRepoConfig.map(_.updates.keep(update)) match {
          case Some(Left(reason)) =>
            F.pure(UpdateRejectedByConfig(dependency, reason))
          case _ =>
            pullRequestRepo.findPullRequest(repo, dependency, update.nextVersion).map {
              case None =>
                DependencyOutdated(dependency, update)
              case Some((uri, _, state)) if state === Closed =>
                PullRequestClosed(dependency, update, uri)
              case Some((uri, baseSha1, _)) if baseSha1 === repoCache.sha1 =>
                PullRequestUpToDate(dependency, update, uri)
              case Some((uri, _, _)) =>
                PullRequestOutdated(dependency, update, uri)
            }
        }
    }
}

object UpdateService {
  def isUpdateFor(update: Update, dependency: Dependency): Boolean =
    update.groupId === dependency.groupId &&
      update.artifactIds.contains_(dependency.artifactId) &&
      update.currentVersion === dependency.version

  def includeInUpdateCheck(dependency: Dependency): Boolean =
    (dependency.groupId, dependency.artifactId) match {
      case ("com.ccadllc.cedi", "build")                     => false
      case ("com.codecommit", "sbt-spiewak-sonatype")        => false
      case ("com.gilt.sbt", "sbt-newrelic")                  => false
      case ("com.iheart", "sbt-play-swagger")                => false
      case ("com.nrinaudo", "kantan.sbt-kantan")             => false
      case ("com.slamdata", "sbt-quasar-datasource")         => false
      case ("com.typesafe.play", "interplay")                => false
      case ("org.foundweekends.giter8", "sbt-giter8")        => false
      case ("org.scalablytyped", "sbt-scalablytyped")        => false
      case ("org.scala-lang.modules", "sbt-scala-module")    => false
      case ("org.scala-lang.modules", "scala-module-plugin") => false
      case ("org.scodec", "scodec-build")                    => false
      case ("org.xerial.sbt", "sbt-pack")                    => false
      case _                                                 => true
    }

  def getNewerGroupId(currentGroupId: String, artifactId: String): Option[(String, String)] =
    Option((currentGroupId, artifactId) match {
      case ("org.spire-math", "kind-projector") => ("org.typelevel", "0.10.0")
      case ("com.geirsson", "sbt-scalafmt")     => ("org.scalameta", "2.0.0")
      case ("net.ceedubs", "ficus")             => ("com.iheart", "1.3.4")
      case _                                    => ("", "")
    }).filter { case (groupId, _) => groupId.nonEmpty }

  def findUpdateUnderNewGroup(dep: Dependency): Option[Update.Single] =
    getNewerGroupId(dep.groupId, dep.artifactId).map {
      case (newId, fromVersion) =>
        dep.toUpdate.copy(newerGroupId = Some(newId), newerVersions = util.Nel.of(fromVersion))
    }
}
