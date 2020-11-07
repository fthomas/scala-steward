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

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import org.http4s.Uri
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.{Dependency, Scope}
import org.scalasteward.core.scaladex.ScaladexAlg
import org.scalasteward.core.{util, vcs}

trait UrlMappingAlg[F[_]] {
  def getArtifactIdUrlMapping(dependencies: Scope[List[Dependency]]): F[Map[String, Uri]]
}

object UrlMappingAlg {
  def create[F[_]](implicit
      coursierAlg: CoursierAlg[F],
      scaladexAlg: ScaladexAlg[F],
      logger: Logger[F],
      F: Sync[F]
  ): UrlMappingAlg[F] = new UrlMappingAlg[F] {
    override def getArtifactIdUrlMapping(
        dependencies: Scope[List[Dependency]]
    ): F[Map[String, Uri]] =
      for {
        coursierResult <- coursierAlg.getArtifactIdUrlMapping(dependencies)
        notFoundInCoursier = dependencies.value.filter { dep =>
          !coursierResult.get(dep.artifactId.name).exists(uri => vcs.isVcsUrl(uri.toString()))
        }
        scaladexResult <- scaladexAlg.getArtifactIdUrlMapping(notFoundInCoursier)
        _ <- logger.info(s"Complement ${scaladexResult.size} artifacts' SCM URL from scaladex")
        merged = util.combineMapLastWin(coursierResult, scaladexResult)
      } yield merged
  }
}
