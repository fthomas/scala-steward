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

package org.scalasteward.core.repoconfig

import cats.implicits._
import cats.{Eq, Monoid}
import eu.timepit.refined.types.numeric.NonNegInt
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.refined._
import io.circe.{Codec, Decoder}
import org.scalasteward.core.data.{GroupId, Update}
import org.scalasteward.core.update.FilterAlg.{
  FilterResult,
  IgnoredByConfig,
  NotAllowedByConfig,
  VersionPinnedByConfig
}
import org.scalasteward.core.util.{combineOptions, Nel}

final case class UpdatesConfig(
    pin: List[UpdatePattern] = List.empty,
    allow: List[UpdatePattern] = List.empty,
    ignore: List[UpdatePattern] = List.empty,
    limit: Option[NonNegInt] = None,
    fileExtensions: Option[List[String]] = None
) {
  def fileExtensionsOrDefault: Set[String] =
    fileExtensions.fold(UpdatesConfig.defaultFileExtensions)(_.toSet)

  def keep(update: Update.Single): FilterResult =
    isAllowed(update).flatMap(isPinned).flatMap(isIgnored)

  private def isAllowed(update: Update.Single): FilterResult = {
    val m = UpdatePattern.findMatch(allow, update, include = true)
    if (m.filteredVersions.nonEmpty)
      Right(update.copy(newerVersions = Nel.fromListUnsafe(m.filteredVersions)))
    else if (allow.isEmpty)
      Right(update)
    else Left(NotAllowedByConfig(update))
  }

  private def isPinned(update: Update.Single): FilterResult = {
    val m = UpdatePattern.findMatch(pin, update, include = true)
    if (m.filteredVersions.nonEmpty)
      Right(update.copy(newerVersions = Nel.fromListUnsafe(m.filteredVersions)))
    else if (m.byArtifactId.isEmpty)
      Right(update)
    else Left(VersionPinnedByConfig(update))
  }

  private def isIgnored(update: Update.Single): FilterResult = {
    val m = UpdatePattern.findMatch(ignore, update, include = false)
    if (m.filteredVersions.nonEmpty)
      Right(update.copy(newerVersions = Nel.fromListUnsafe(m.filteredVersions)))
    else
      Left(IgnoredByConfig(update))
  }
}

object UpdatesConfig {
  val defaultFileExtensions: Set[String] =
    Set(".mill-version", ".scala", ".sbt", ".sbt.shared", ".sc", ".yml", "pom.xml")

  implicit val updatesConfigEq: Eq[UpdatesConfig] =
    Eq.fromUniversalEquals

  implicit val updatesConfigConfiguration: Configuration =
    Configuration.default.withDefaults

  implicit val updatesConfigCodec: Codec[UpdatesConfig] =
    deriveConfiguredCodec

  implicit val updatesConfigMonoid: Monoid[UpdatesConfig] =
    Monoid.instance(
      UpdatesConfig(),
      (x, y) =>
        UpdatesConfig(
          pin = mergePin(x.pin, y.pin),
          allow = mergeAllow(x.allow, y.allow),
          ignore = mergeIgnore(x.ignore, y.ignore),
          limit = x.limit.orElse(y.limit),
          fileExtensions = mergeFileExtensions(x.fileExtensions, y.fileExtensions)
        )
    )

  //  Strategy: union with repo preference in terms of revision
  private[repoconfig] def mergePin(
      x: List[UpdatePattern],
      y: List[UpdatePattern]
  ): List[UpdatePattern] =
    x ::: y.filterNot { p1 =>
      x.exists(p2 => p1.groupId === p2.groupId && p1.artifactId === p2.artifactId)
    }

  private[repoconfig] val nonExistingUpdatePattern: List[UpdatePattern] =
    List(UpdatePattern(GroupId("non-exist"), None, None))

  //  Strategy: superset
  //  Xa.Ya.Za |+| Xb.Yb.Zb
  private[repoconfig] def mergeAllow(
      x: List[UpdatePattern],
      y: List[UpdatePattern]
  ): List[UpdatePattern] =
    (x, y) match {
      case (Nil, second) => second
      case (first, Nil)  => first
      case _             =>
        //  remove duplicates first by calling .distinct
        val xm: Map[GroupId, List[UpdatePattern]] = x.distinct.groupBy(_.groupId)
        val ym: Map[GroupId, List[UpdatePattern]] = y.distinct.groupBy(_.groupId)
        val builder = new collection.mutable.ListBuffer[UpdatePattern]()

        //  first of all, we only allow intersection (superset)
        val keys = xm.keySet.intersect(ym.keySet)

        keys.foreach { groupId =>
          builder ++= mergeAllowGroupId(xm(groupId), ym(groupId))
        }

        if (builder.isEmpty) nonExistingUpdatePattern
        else builder.distinct.toList
    }

  //  merge UpdatePattern for same group id
  private def mergeAllowGroupId(
      x: List[UpdatePattern],
      y: List[UpdatePattern]
  ): List[UpdatePattern] =
    (x.exists(_.isWholeGroupIdAllowed), y.exists(_.isWholeGroupIdAllowed)) match {
      case (true, _) => y
      case (_, true) => x
      case _         =>
        //  case with concrete artifacts / versions
        val builder = new collection.mutable.ListBuffer[UpdatePattern]()
        val xByArtifacts = x.groupBy(_.artifactId)
        val yByArtifacts = y.groupBy(_.artifactId)

        x.foreach { updatePattern =>
          if (satisfyUpdatePattern(updatePattern, yByArtifacts))
            builder += updatePattern
        }
        y.foreach { updatePattern =>
          if (satisfyUpdatePattern(updatePattern, xByArtifacts))
            builder += updatePattern
        }

        if (builder.isEmpty) nonExistingUpdatePattern
        else builder.toList
    }

  private def satisfyUpdatePattern(
      targetUpdatePattern: UpdatePattern,
      comparedUpdatePatternsByArtifact: Map[Option[String], List[UpdatePattern]]
  ): Boolean =
    comparedUpdatePatternsByArtifact.get(targetUpdatePattern.artifactId).exists { matchedVersions =>
      //  For simplicity I'm using direct equals here between versions. Feel free to make it more advanced
      matchedVersions.exists(up => up.version.isEmpty || up.version === targetUpdatePattern.version)
    }

  //  Strategy: union
  private[repoconfig] def mergeIgnore(
      x: List[UpdatePattern],
      y: List[UpdatePattern]
  ): List[UpdatePattern] =
    x ::: y.filterNot(x.contains)

  private[repoconfig] def mergeFileExtensions(
      x: Option[List[String]],
      y: Option[List[String]]
  ): Option[List[String]] =
    combineOptions(x, y)(_.intersect(_))

  // prevent IntelliJ from removing the import of io.circe.refined._
  locally(refinedDecoder: Decoder[NonNegInt])
}
