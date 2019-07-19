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

package org.scalasteward.core.io

import java.nio.file.Files

import better.files.File
import cats.FlatMap
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.scalasteward.core.application.Config
import org.scalasteward.core.vcs.data.Repo

import scala.annotation.tailrec

trait WorkspaceAlg[F[_]] {
  def cleanWorkspace: F[Unit]

  def rootDir: F[File]

  def repoDir(repo: Repo): F[File]

  def findSubProjectDirs(repo: Repo): F[List[File]]
}

object WorkspaceAlg {
  def create[F[_]](
      implicit
      fileAlg: FileAlg[F],
      logger: Logger[F],
      config: Config,
      F: FlatMap[F]
  ): WorkspaceAlg[F] =
    new WorkspaceAlg[F] {
      private[this] val reposDir = config.workspace / "repos"

      override def cleanWorkspace: F[Unit] =
        for {
          _ <- logger.info(s"Clean workspace ${config.workspace}")
          _ <- fileAlg.deleteForce(reposDir)
          _ <- rootDir
        } yield ()

      override def rootDir: F[File] =
        fileAlg.ensureExists(config.workspace)

      override def repoDir(repo: Repo): F[File] =
        fileAlg.ensureExists(reposDir / repo.owner / repo.repo)

      override def findSubProjectDirs(repo: Repo): F[List[File]] = {
        @tailrec
        def findSubProject(files: List[File], acc: List[File]): List[File] = files match {
          case Nil => acc
          case file :: rest =>
            if (Files.exists(file.path.resolve("build.sbt"))) findSubProject(rest, file :: acc)
            else findSubProject(file.children.filter(_.isDirectory).toList ::: rest, acc)
        }
        repoDir(repo).map { rootDir =>
          findSubProject(List(rootDir), Nil)
        }
      }
    }
}
