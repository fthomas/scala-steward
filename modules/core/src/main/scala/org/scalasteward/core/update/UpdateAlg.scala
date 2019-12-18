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

import cats.Monad
import cats.implicits._
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Update}
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel

final class UpdateAlg[F[_]](
    implicit
    coursierAlg: CoursierAlg[F],
    F: Monad[F]
) {
  def findUpdate(dependency: Dependency): F[Option[Update.Single]] =
    for {
      newerVersions0 <- coursierAlg.getNewerVersions(dependency)
      maybeUpdate0 = Nel.fromList(newerVersions0).map { newerVersions1 =>
        dependency.toUpdate.copy(newerVersions = newerVersions1.map(_.value))
      }
      maybeUpdate1 = maybeUpdate0.orElse(UpdateAlg.findUpdateUnderNewGroup(dependency))
    } yield maybeUpdate1
}

object UpdateAlg {
  def isUpdateFor(update: Update, dependency: Dependency): Boolean =
    update.groupId === dependency.groupId &&
      update.artifactIds.exists { artifactId =>
        artifactId.name === dependency.artifactId.name &&
        dependency.artifactId.crossNames.forall(artifactId.crossNames.contains)
      } &&
      update.currentVersion === dependency.version

  def getNewerGroupId(currentGroupId: GroupId, artifactId: ArtifactId): Option[(GroupId, String)] =
    Some((currentGroupId.value, artifactId.name)).collect {
      case ("org.spire-math", "kind-projector")   => (GroupId("org.typelevel"), "0.10.0")
      case ("com.github.mpilquist", "simulacrum") => (GroupId("org.typelevel"), "1.0.0")
      case ("com.geirsson", "sbt-scalafmt")       => (GroupId("org.scalameta"), "2.0.0")
      case ("net.ceedubs", "ficus")               => (GroupId("com.iheart"), "1.3.4")
    }

  def findUpdateUnderNewGroup(dep: Dependency): Option[Update.Single] =
    getNewerGroupId(dep.groupId, dep.artifactId).map {
      case (newId, fromVersion) =>
        dep.toUpdate.copy(newerGroupId = Some(newId), newerVersions = util.Nel.of(fromVersion))
    }
}
