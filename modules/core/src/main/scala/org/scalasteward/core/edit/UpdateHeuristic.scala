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

package org.scalasteward.core.edit

import cats.Foldable
import cats.syntax.all._
// import org.scalasteward.core.buildtool.mill.MillAlg
import org.scalasteward.core.data.Update
import org.scalasteward.core.scalafmt.isScalafmtUpdate
import org.scalasteward.core.util
import org.scalasteward.core.util.Nel

import scala.util.matching.Regex

/** `UpdateHeuristic` is a wrapper for a function that takes an `Update` and
  * returns a new function that replaces the current version with the next
  * version in its input. The result type indicates whether the input was
  * modified or not.
  */
final case class UpdateHeuristic(
    name: String,
    replaceVersion: Update => String => Option[String]
)

object UpdateHeuristic {

  /** Removes punctuation from the input and returns it as regex that allows
    * punctuation between characters.
    */
  private def toFlexibleRegex(string: String): String = {
    val punctuation = List('.', '-', '_')
    val allowPunctuation = "[\\.\\-_]*"
    string.toList
      .collect { case c if !punctuation.contains_(c) => Regex.quote(c.toString) }
      .intercalate(allowPunctuation)
  }

  private def alternation[F[_]: Foldable](strings: F[String]): String =
    strings.mkString_("(", "|", ")")

  private def shouldBeIgnored(prefix: String): Boolean =
    prefix.toLowerCase.contains("previous") || prefix.trim.startsWith("//")

  private def moduleIdRegex(version: String): Regex = {
    val ident = """[^":\n]+"""
    val v = Regex.quote(version)
    raw"""(.*)(["|`]($ident)(?:"\s*%+\s*"|:+)($ident)(?:"\s*%\s*|:+))("?)($v)("|`)""".r
  }

  private def replaceArtifactF(update: Update): String => Option[String] = { target =>
    update match {
      case s @ Update.Single(_, _, Some(newerGroupId), Some(newerArtifactId)) =>
        val currentGroupId = Regex.quote(s.groupId.value)
        val currentArtifactId = Regex.quote(s.artifactId.name)
        val regex = s"""(?i)(.*)$currentGroupId(.*)$currentArtifactId""".r
        replaceSomeInAllowedParts(
          regex,
          target,
          match0 => {
            val group1 = match0.group(1)
            val group2 = match0.group(2)
            Some(s"""$group1$newerGroupId$group2$newerArtifactId""")
          }
        ).someIfChanged
      case _ => Some(target)
    }
  }

  private def defaultReplaceVersion(
      getSearchTerms: Update => List[String],
      getPrefixRegex: Update => Option[String] = _ => None
  ): Update => String => Option[String] = {
    def searchTermsToAlternation(terms: List[String]): Option[String] =
      Nel.fromList(terms.map(toFlexibleRegex).filterNot(_.isEmpty)).map(alternation(_))

    def mkRegex(update: Update): Option[Regex] =
      searchTermsToAlternation(getSearchTerms(update).map(removeCommonSuffix)).map { searchTerms =>
        val prefix = getPrefixRegex(update).getOrElse("")
        val currentVersion = Regex.quote(update.currentVersion)
        s"(?i)(.*)($prefix$searchTerms.*?)$currentVersion(.?)".r
      }

    def replaceVersionF(update: Update): String => Option[String] =
      mkRegex(update).fold((_: String) => Option.empty[String]) { regex => target =>
        replaceSomeInAllowedParts(
          regex,
          target,
          match0 => {
            val prefix = match0.group(1)
            val dependency = match0.group(2)
            val versionSuffix = match0.group(match0.groupCount)
            Option.when {
              !shouldBeIgnored(prefix) &&
              enclosingCharsDelimitVersion(dependency.lastOption, versionSuffix.headOption) &&
              !moduleIdRegex(update.currentVersion).matches(match0.matched)
            } {
              Regex.quoteReplacement(prefix + dependency + update.nextVersion + versionSuffix)
            }
          }
        ).someIfChanged
      }

    update => target => replaceVersionF(update)(target) >>= replaceArtifactF(update)
  }

  private def enclosingCharsDelimitVersion(before: Option[Char], after: Option[Char]): Boolean =
    (before, after) match {
      case (Some('"'), c2) => c2.contains_('"')
      case (_, Some('"'))  => false

      case _ => true
    }

  private def searchTerms(update: Update): List[String] = {
    val terms = update match {
      case s: Update.Single => Nel.one(s.artifactId.name)
      case g: Update.Group =>
        g.artifactIds.map(_.name).concat(g.artifactIdsPrefix.map(_.value).toList)
    }
    terms.map(Update.nameOf(update.groupId, _)).toList
  }

  private def removeCommonSuffix(str: String): String =
    util.string.removeSuffix(str, Update.commonSuffixes)

  private def isCommonWord(s: String): Boolean =
    s === "scala"

  val moduleId: UpdateHeuristic = UpdateHeuristic(
    name = "moduleId",
    replaceVersion = update =>
      target =>
        replaceSomeInAllowedParts(
          moduleIdRegex(update.currentVersion),
          target,
          match0 => {
            val prefix = match0.group(1)
            val dependency = match0.group(2)
            val groupId = match0.group(3)
            val artifactId = match0.group(4)
            val versionPrefix = match0.group(5)
            val versionSuffix = match0.group(7)
            Option.when {
              !shouldBeIgnored(prefix) &&
              update.groupId.value === groupId &&
              update.artifactIds.exists(_.name === artifactId)
            } {
              Regex.quoteReplacement(
                s"""$prefix$dependency$versionPrefix${update.nextVersion}$versionSuffix"""
              )
            }
          }
        ).someIfChanged >>= replaceArtifactF(update)
  )

  val strict: UpdateHeuristic = UpdateHeuristic(
    name = "strict",
    replaceVersion = defaultReplaceVersion(searchTerms, update => Some(s"${update.groupId}.*?"))
  )

  val original: UpdateHeuristic = UpdateHeuristic(
    name = "original",
    replaceVersion = defaultReplaceVersion(searchTerms)
  )

  val relaxed: UpdateHeuristic = UpdateHeuristic(
    name = "relaxed",
    replaceVersion = defaultReplaceVersion { update =>
      util.string.extractWords(update.mainArtifactId).filterNot(isCommonWord)
    }
  )

  val sliding: UpdateHeuristic = UpdateHeuristic(
    name = "sliding",
    replaceVersion = defaultReplaceVersion(
      _.mainArtifactId.toSeq.sliding(5).map(_.unwrap).filterNot(isCommonWord).toList
    )
  )

  val completeGroupId: UpdateHeuristic = UpdateHeuristic(
    name = "completeGroupId",
    replaceVersion = defaultReplaceVersion(update => List(update.groupId.value))
  )

  val groupId: UpdateHeuristic = UpdateHeuristic(
    name = "groupId",
    replaceVersion = defaultReplaceVersion(
      _.groupId.value
        .split('.')
        .toList
        .drop(1)
        .flatMap(util.string.extractWords)
        .filter(_.length > 3)
    )
  )

  val specific: UpdateHeuristic = UpdateHeuristic(
    name = "specific",
    replaceVersion = {
      case update: Update.Single if isScalafmtUpdate(update) =>
        defaultReplaceVersion(_ => List("version"))(update)
//      case update: Update.Single if MillAlg.isMillMainUpdate(update) =>
//        // TODO: fill in the missing pieces
      case _ =>
        _ => None
    }
  )

  val all: Nel[UpdateHeuristic] =
    Nel.of(moduleId, strict, original, relaxed, sliding, completeGroupId, groupId, specific)
}
