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

package org.scalasteward.core.application

import better.files.File
import cats.Monad
import cats.effect.ExitCode
import cats.syntax.all._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.buildtool.sbt.SbtAlg
import org.scalasteward.core.git.{Branch, GitAlg}
import org.scalasteward.core.io.{FileAlg, WorkspaceAlg}
import org.scalasteward.core.nurture.NurtureAlg
import org.scalasteward.core.repocache.RepoCacheAlg
import org.scalasteward.core.update.PruningAlg
import org.scalasteward.core.util
import org.scalasteward.core.util.DateTimeAlg
import org.scalasteward.core.util.logger.LoggerOps
import org.scalasteward.core.vcs.data.Repo

final class StewardAlg[F[_]](implicit
    config: Config,
    dateTimeAlg: DateTimeAlg[F],
    fileAlg: FileAlg[F],
    gitAlg: GitAlg[F],
    logger: Logger[F],
    nurtureAlg: NurtureAlg[F],
    pruningAlg: PruningAlg[F],
    repoCacheAlg: RepoCacheAlg[F],
    sbtAlg: SbtAlg[F],
    selfCheckAlg: SelfCheckAlg[F],
    streamCompiler: Stream.Compiler[F, F],
    workspaceAlg: WorkspaceAlg[F],
    F: Monad[F]
) {
  private def printBanner: F[Unit] = {
    val banner =
      """|  ____            _         ____  _                             _
         | / ___|  ___ __ _| | __ _  / ___|| |_ _____      ____ _ _ __ __| |
         | \___ \ / __/ _` | |/ _` | \___ \| __/ _ \ \ /\ / / _` | '__/ _` |
         |  ___) | (_| (_| | | (_| |  ___) | ||  __/\ V  V / (_| | | | (_| |
         | |____/ \___\__,_|_|\__,_| |____/ \__\___| \_/\_/ \__,_|_|  \__,_|""".stripMargin
    val msg = List(" ", banner, s" v${org.scalasteward.core.BuildInfo.version}", " ")
      .mkString(System.lineSeparator())
    logger.info(msg)
  }

  private def readRepos(reposFile: File): F[List[Repo]] =
    fileAlg.readFile(reposFile).map { maybeContent =>
      val regex = """-\s+(.+)/([^#\n]+)(?:#(.+))?""".r
      val content = maybeContent.getOrElse("")
      content.linesIterator.collect {
        case regex(owner, repo, null)   => Repo(owner.trim, repo.trim, branch = None)
        case regex(owner, repo, branch) => Repo(owner.trim, repo.trim, Some(Branch(branch)))
      }.toList
    }

  private def steward(repo: Repo): F[Either[Throwable, Unit]] = {
    val label = s"Steward ${repo.show}"
    logger.infoTotalTime(label) {
      for {
        _ <- logger.info(util.string.lineLeftRight(label))
        _ <- repoCacheAlg.checkCache(repo)
        (attentionNeeded, updates) <- pruningAlg.needsAttention(repo)
        result <- {
          if (attentionNeeded) nurtureAlg.nurture(repo, updates)
          else gitAlg.removeClone(repo).as(().asRight[Throwable])
        }
      } yield result
    }
  }

  def runF: F[ExitCode] =
    logger.infoTotalTime("run") {
      for {
        _ <- printBanner
        _ <- selfCheckAlg.checkAll
        exitCode <- sbtAlg.addGlobalPlugins {
          for {
            _ <- workspaceAlg.cleanWorkspace
            repos <- readRepos(config.reposFile)
            result <- Stream.emits(repos).evalMap(steward).compile.foldMonoid
          } yield result.fold(_ => ExitCode.Error, _ => ExitCode.Success)
        }
      } yield exitCode
    }
}
