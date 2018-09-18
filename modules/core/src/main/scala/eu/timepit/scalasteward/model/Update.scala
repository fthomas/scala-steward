/*
 * Copyright 2018 scala-steward contributors
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

package eu.timepit.scalasteward.model

import cats.data.NonEmptyList
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.scalasteward.model.Update.{Group, Single}
import eu.timepit.scalasteward.util

import scala.util.matching.Regex

sealed trait Update {
  def artifactId: String
  def currentVersion: String
  def groupId: String
  def name: String
  def nextVersion: String
  def show: String

  def replaceAllIn(target: String): Option[String] = {
    val quoted = searchTerms.map { term =>
      Regex
        .quoteReplacement(Update.removeCommonSuffix(term))
        .replace("-", ".?")
    }
    val keyword = if (quoted.tail.isEmpty) quoted.head else quoted.mkString_("(", "|", ")")
    val regex = s"(?i)($keyword.*?)${Regex.quote(currentVersion)}".r
    var updated = false
    val result = regex.replaceAllIn(target, m => {
      updated = true
      m.group(1) + nextVersion
    })
    if (updated) Some(result) else None
  }

  def searchTerms: NonEmptyList[String] =
    this match {
      case s: Single => NonEmptyList.one(s.artifactId)
      case g: Group  => g.artifactIds.concat(g.artifactIdsPrefix.map(_.value).toList)
    }
}

object Update {
  final case class Single(
      groupId: String,
      artifactId: String,
      currentVersion: String,
      newerVersions: NonEmptyList[String]
  ) extends Update {
    override def name: String =
      if (commonSuffixes.contains(artifactId))
        groupId.split('.').lastOption.getOrElse(groupId)
      else
        artifactId

    override def nextVersion: String =
      newerVersions.head

    override def show: String =
      s"$groupId:$artifactId : ${(currentVersion :: newerVersions).mkString_("", " -> ", "")}"
  }

  final case class Group(
      updates: NonEmptyList[Single]
  ) extends Update {
    def artifactIds: NonEmptyList[String] =
      updates.map(_.artifactId)

    override def artifactId: String =
      updates.head.artifactId

    override def currentVersion: String =
      updates.head.currentVersion

    override def groupId: String =
      updates.head.groupId

    override def name: String =
      updates.head.name

    override def nextVersion: String =
      updates.head.nextVersion

    override def show: String =
      updates.map(_.show).mkString_("", ", ", "")

    def artifactIdsPrefix: Option[NonEmptyString] =
      util.longestCommonNonEmptyPrefix(artifactIds)
  }

  ///

  def fromString(str: String): Either[Throwable, Single] =
    Either.catchNonFatal {
      val regex = """([^\s:]+):([^\s:]+)[^\s]*\s+:\s+([^\s]+)\s+->(.+)""".r
      str match {
        case regex(groupId, artifactId, version, updates) =>
          val newerVersions = NonEmptyList.fromListUnsafe(updates.split("->").map(_.trim).toList)
          Single(groupId, artifactId, version, newerVersions)
      }
    }

  val commonSuffixes: List[String] =
    List("core", "server")

  def removeCommonSuffix(str: String): String =
    util.removeSuffix(str, commonSuffixes)
}
