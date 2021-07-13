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

package org.scalasteward.core.data

import cats.syntax.all._
import org.scalasteward.core.data.SemVerSpec.Change._

final case class SemVerSpec(
    major: String,
    minor: String,
    patch: String,
    preRelease: Option[String] = None,
    buildMetadata: Option[String] = None
)

object SemVerSpec {
  def parse(s: String): Option[SemVerSpec] =
    cats.parse.SemVer.semver.parseAll(s).toOption.map { v =>
      SemVerSpec(v.core.major, v.core.minor, v.core.patch, v.preRelease, v.buildMetadata)
    }

  sealed abstract class Change(val render: String)
  object Change {
    case object Major extends Change("major")
    case object Minor extends Change("minor")
    case object Patch extends Change("patch")
    case object PreRelease extends Change("pre-release")
    case object BuildMetadata extends Change("build-metadata")
  }

  def getChange(from: SemVerSpec, to: SemVerSpec): Option[Change] =
    if (from.major =!= to.major) Some(Major)
    else if (from.minor =!= to.minor) Some(Minor)
    else if (from.preRelease =!= to.preRelease) Some(PreRelease)
    else if (from.patch =!= to.patch) Some(Patch)
    else if (from.buildMetadata =!= to.buildMetadata) Some(BuildMetadata)
    else None
}
