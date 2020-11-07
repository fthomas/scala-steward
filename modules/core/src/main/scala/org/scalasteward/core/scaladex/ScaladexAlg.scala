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

package org.scalasteward.core.scaladex

import cats.effect.Sync
import cats.implicits._
import org.http4s.Uri
import org.scalasteward.core.data.Dependency

trait ScaladexAlg[F[_]] {
  def getArtifactUrl(dependency: Dependency): F[Option[Uri]]
  def getArtifactIdUrlMapping(dependencies: List[Dependency]): F[Map[String, Uri]]
}

object ScaladexAlg {
  def create[F[_]](implicit
      client: HttpScaladexClient[F],
      F: Sync[F]
  ): ScaladexAlg[F] =
    new ScaladexAlg[F] {
      override def getArtifactUrl(dependency: Dependency): F[Option[Uri]] =
        client.findGitHubUrl(dependency.artifactId.name)

      override def getArtifactIdUrlMapping(dependencies: List[Dependency]): F[Map[String, Uri]] =
        dependencies
          .traverseFilter(dep => getArtifactUrl(dep).map(_.map(dep.artifactId.name -> _)))
          .map(_.toMap)
    }
}
