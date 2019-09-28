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

package org.scalasteward.core.data

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.util.Nel

final case class Dependency(
    groupId: String,
    artifactId: String,
    crossArtifactIds: List[String],
    version: String,
    newerVersions: Option[List[String]] = None,
    configurations: Option[String] = None,
    origin: Option[String] = None,
    sbtSeries: Option[String] = None
) {
  def artifactIdCross: String =
    crossArtifactIds.sorted.headOption.getOrElse(artifactId)

  def formatAsModuleId: String =
    s""""$groupId" % "$artifactIdCross" % "$version""""

  def formatAsModuleIdScalaVersionAgnostic: String =
    s""""$groupId" %% "$artifactId" % "$version""""

  def toUpdate: Update.Single =
    Update.Single(groupId, artifactId, version, Nel.of(version), configurations)
}

object Dependency {
  implicit val dependencyDecoder: Decoder[Dependency] =
    deriveDecoder

  implicit val dependencyEncoder: Encoder[Dependency] =
    deriveEncoder
}
