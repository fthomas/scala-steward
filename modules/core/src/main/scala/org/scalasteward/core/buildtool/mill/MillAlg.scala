/*
 * Copyright 2018-2021 Scala Steward contributors
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

package org.scalasteward.core.buildtool.mill

import better.files.File
import cats.effect.MonadCancelThrow
import cats.syntax.all._
import org.scalasteward.core.buildtool.BuildToolAlg
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Resolver, Scope, Update}
import org.scalasteward.core.data.Scope.Dependencies
import org.scalasteward.core.edit.scalafix.ScalafixMigration
import org.scalasteward.core.io.{FileAlg, ProcessAlg, WorkspaceAlg}
import org.scalasteward.core.util.Nel
import org.scalasteward.core.vcs.data.BuildRoot

trait MillAlg[F[_]] extends BuildToolAlg[F]

object MillAlg {
  private val content =
    s"""|import coursierapi.MavenRepository
        |
        |interp.repositories() ++= Seq(
        |  MavenRepository.of("https://oss.sonatype.org/content/repositories/snapshots/")
        |)
        |interp.load.ivy("${org.scalasteward.core.BuildInfo.organization}" %% "${org.scalasteward.core.BuildInfo.millPluginModuleName}" % "${org.scalasteward.core.BuildInfo.version}")
        |""".stripMargin

  val extractDeps: String =
    s"${org.scalasteward.core.BuildInfo.millPluginModuleRootPkg}.StewardPlugin/extractDeps"

  def create[F[_]](implicit
      fileAlg: FileAlg[F],
      processAlg: ProcessAlg[F],
      workspaceAlg: WorkspaceAlg[F],
      F: MonadCancelThrow[F]
  ): MillAlg[F] =
    new MillAlg[F] {
      override def containsBuild(buildRoot: BuildRoot): F[Boolean] =
        workspaceAlg
          .buildRootDir(buildRoot)
          .flatMap(buildRootDir => fileAlg.isRegularFile(buildRootDir / "build.sc"))

      override def getDependencies(buildRoot: BuildRoot): F[List[Dependencies]] =
        for {
          buildRootDir <- workspaceAlg.buildRootDir(buildRoot)
          predef = buildRootDir / "scala-steward.sc"
          extracted <- fileAlg.createTemporarily(predef, content) {
            val command = Nel("mill", List("-i", "-p", predef.toString, "show", extractDeps))
            processAlg.execSandboxed(command, buildRootDir)
          }
          parsed <- F.fromEither(
            parser.parseModules(extracted.dropWhile(!_.startsWith("{")).mkString("\n"))
          )
          dependencies = parsed.map(module => Scope(module.dependencies, module.repositories))
          millBuildVersion <- getMillVersion(buildRootDir)
          millBuildDeps = millBuildVersion.toSeq.map(version =>
            Scope(List(millMainArtifact(version)), List(millMainResolver))
          )
        } yield dependencies ++ millBuildDeps

      override def runMigration(buildRoot: BuildRoot, migration: ScalafixMigration): F[Unit] =
        F.unit

      def getMillVersion(buildRootDir: File): F[Option[String]] =
        for {
          millVersionFileContent <- fileAlg.readFile(buildRootDir / ".mill-version")
          version = millVersionFileContent.flatMap(parser.parseMillVersion)
        } yield version

    }

  private[this] val millMainResolver: Resolver = Resolver.mavenCentral
  private[this] val millMainGroupId = GroupId("com.lihaoyi")
  private[this] val millMainArtifactId = ArtifactId("mill-main")

  private def millMainArtifact(version: String): Dependency =
    Dependency(millMainGroupId, millMainArtifactId, version)

  def isMillMainUpdate(update: Update.Single): Boolean =
    update.groupId === millMainGroupId && update.artifactId.name === millMainArtifactId.name

}
