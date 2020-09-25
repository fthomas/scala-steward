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

package org.scalasteward.core.edit

import better.files.File
import cats.Traverse
import cats.syntax.all._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.buildtool.BuildToolDispatcher
import org.scalasteward.core.data.Update
import org.scalasteward.core.io.{isSourceFile, FileAlg, WorkspaceAlg}
import org.scalasteward.core.repoconfig.RepoConfigAlg
import org.scalasteward.core.scalafix.MigrationAlg
import org.scalasteward.core.util._
import org.scalasteward.core.vcs.data.Repo

final class EditAlg[F[_]](implicit
    buildToolDispatcher: BuildToolDispatcher[F],
    fileAlg: FileAlg[F],
    logger: Logger[F],
    migrationAlg: MigrationAlg,
    streamCompiler: Stream.Compiler[F, F],
    workspaceAlg: WorkspaceAlg[F],
    repoConfigAlg: RepoConfigAlg[F],
    F: MonadThrowable[F]
) {
  def applyUpdate(repo: Repo, update: Update, fileExtensions: Set[String]): F[Unit] =
    for {
      _ <- applyScalafixMigrations(repo, update).handleErrorWith(e =>
        logger.warn(s"Could not apply ${update.show} : $e")
      )
      repoDir <- workspaceAlg.repoDir(repo)
      files <- fileAlg.findFilesContaining(
        repoDir,
        update.currentVersion,
        isSourceFile(update, fileExtensions)
      )
      noFilesFound = logger.warn("No files found that contain the current version")
      config <- repoConfigAlg.readRepoConfigOrDefault(repo)
      _ <- files.toNel.fold(noFilesFound)(applyUpdateTo(_, update, config.updates.customHeuristics))
    } yield ()

  def applyUpdateTo[G[_]: Traverse](
      files: G[File],
      update: Update,
      customHeuristics: List[CustomHeuristic] = Nil
  ): F[Unit] = {
    val actions: Nel[F[Boolean]] = UpdateHeuristic.all(customHeuristics = customHeuristics).map {
      heuristic =>
        logger.info(s"Trying heuristic '${heuristic.name}'") >>
          fileAlg.editFiles(files, heuristic.replaceVersion(update))
    }
    bindUntilTrue(actions).void
  }

  def applyScalafixMigrations(repo: Repo, update: Update): F[Unit] =
    Nel.fromList(migrationAlg.findMigrations(update)).traverse_ { migrations =>
      logger.info(s"Applying migrations: $migrations") >>
        buildToolDispatcher.runMigrations(repo, migrations)
    }
}
