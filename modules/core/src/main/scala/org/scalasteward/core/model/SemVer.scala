/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core.model

import cats.implicits._
import eu.timepit.refined.cats.refTypeEq
import eu.timepit.refined.types.numeric.NonNegBigInt
import eu.timepit.refined.types.string.NonEmptyString
import org.scalasteward.core.model.SemVer.Change._
import scala.util.Try

final case class SemVer(
    major: NonNegBigInt,
    minor: NonNegBigInt,
    patch: NonNegBigInt,
    preRelease: Option[NonEmptyString],
    buildMetadata: Option[NonEmptyString]
) {
  def render: String =
    s"$major.$minor.$patch" + preRelease.fold("")("-" + _) + buildMetadata.fold("")("+" + _)
}

object SemVer {
  def parse(s: String): Option[SemVer] = {
    def parseNonNegBigInt(s: String): Option[NonNegBigInt] =
      Try(BigInt(s)).toOption.flatMap(NonNegBigInt.unapply)

    def parseIdentifier(s: String): Option[NonEmptyString] =
      Option(s).map(_.drop(1)).flatMap(NonEmptyString.unapply)

    val pattern = raw"""(\d+)\.(\d+)\.(\d+)(\-[^\+]+)?(\+.+)?""".r
    val maybeSemVer = s match {
      case pattern(majorStr, minorStr, patchStr, preReleaseStr, buildMetadataStr) =>
        for {
          major <- parseNonNegBigInt(majorStr)
          minor <- parseNonNegBigInt(minorStr)
          patch <- parseNonNegBigInt(patchStr)
          preRelease = parseIdentifier(preReleaseStr)
          buildMetadata = parseIdentifier(buildMetadataStr)
        } yield SemVer(major, minor, patch, preRelease, buildMetadata)
      case _ => None
    }
    maybeSemVer.filter(_.render === s)
  }

  sealed abstract class Change(val render: String)
  object Change {
    case object Major extends Change("major")
    case object Minor extends Change("minor")
    case object Patch extends Change("patch")
    case object PreRelease extends Change("pre-release")
    case object BuildMetadata extends Change("build-metadata")
  }

  def getChange(from: SemVer, to: SemVer): Option[Change] =
    if (from.major =!= to.major) Some(Major)
    else if (from.minor =!= to.minor) Some(Minor)
    else if (from.patch =!= to.patch) Some(Patch)
    else if (from.preRelease =!= to.preRelease) Some(PreRelease)
    else if (from.buildMetadata =!= to.buildMetadata) Some(BuildMetadata)
    else None
}
