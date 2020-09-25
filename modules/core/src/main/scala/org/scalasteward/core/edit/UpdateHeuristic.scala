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

package org.scalasteward.core.edit

import cats.Foldable
import cats.syntax.all._
import org.scalasteward.core.data.{GroupId, Update}
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
    val allowPunctuation = punctuation.mkString("[", "", "]*")
    string.toList
      .collect { case c if !punctuation.contains_(c) => Regex.quote(c.toString) }
      .intercalate(allowPunctuation)
  }

  private def alternation[F[_]: Foldable](strings: F[String]): String =
    strings.mkString_("(", "|", ")")

  private def shouldBeIgnored(prefix: String): Boolean =
    prefix.toLowerCase.contains("previous") || prefix.trim.startsWith("//")

  private def replaceGroupF(update: Update): String => Option[String] = { target =>
    update match {
      case s @ Update.Single(_, _, Some(newerGroupId)) =>
        val currentGroupId = Regex.quote(s.groupId.value)
        val currentArtifactId = Regex.quote(s.artifactId.name)
        val regex = s"""(?i)(.*)${currentGroupId}(.*${currentArtifactId})""".r
        replaceSomeInAllowedParts(
          regex,
          target,
          match0 => {
            val group1 = match0.group(1)
            val group2 = match0.group(2)
            Some(s"""$group1$newerGroupId$group2""")
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

    def replaceF(update: Update): String => Option[String] =
      target => replaceVersionF(update)(target) >>= replaceGroupF(update)

    def replaceVersionF(update: Update): String => Option[String] =
      mkRegex(update).fold((_: String) => Option.empty[String]) { regex => target =>
        replaceSomeInAllowedParts(
          regex,
          target,
          match0 => {
            val group1 = match0.group(1)
            val group2 = match0.group(2)
            val lastGroup = match0.group(match0.groupCount)
            val versionInQuotes =
              group2.lastOption.filter(_ === '"').fold(true)(lastGroup.headOption.contains_)
            if (shouldBeIgnored(group1) || !versionInQuotes) None
            else Some(Regex.quoteReplacement(group1 + group2 + update.nextVersion + lastGroup))
          }
        ).someIfChanged
      }

    replaceF
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

  val moduleId = UpdateHeuristic(
    name = "moduleId",
    replaceVersion = update =>
      target =>
        {
          val groupId = Regex.quote(update.groupId.value)
          val artifactIds =
            alternation(update.artifactIds.map(artifactId => Regex.quote(artifactId.name)))
          val currentVersion = Regex.quote(update.currentVersion)
          val regex =
            raw"""(.*)(["|`]$groupId(?:"\s*%+\s*"|:+)$artifactIds(?:"\s*%\s*|:+))("?)($currentVersion)("|`)""".r
          replaceSomeInAllowedParts(
            regex,
            target,
            match0 => {
              val precedingCharacters = match0.group(1)
              val dependency = match0.group(2)
              val versionPrefix = match0.group(4)
              val versionSuffix = match0.group(6)
              if (shouldBeIgnored(precedingCharacters)) None
              else
                Some(
                  Regex.quoteReplacement(
                    s"""$precedingCharacters$dependency$versionPrefix${update.nextVersion}$versionSuffix"""
                  )
                )
            }
          ).someIfChanged
        } >>= replaceGroupF(update)
  )

  val strict = UpdateHeuristic(
    name = "strict",
    replaceVersion = defaultReplaceVersion(searchTerms, update => Some(s"${update.groupId}.*?"))
  )

  val original = UpdateHeuristic(
    name = "original",
    replaceVersion = defaultReplaceVersion(searchTerms)
  )

  val relaxed = UpdateHeuristic(
    name = "relaxed",
    replaceVersion = defaultReplaceVersion { update =>
      util.string.extractWords(update.mainArtifactId).filterNot(isCommonWord)
    }
  )

  val sliding = UpdateHeuristic(
    name = "sliding",
    replaceVersion = defaultReplaceVersion(
      _.mainArtifactId.toSeq.sliding(5).map(_.unwrap).filterNot(isCommonWord).toList
    )
  )

  val completeGroupId = UpdateHeuristic(
    name = "completeGroupId",
    replaceVersion = defaultReplaceVersion(update => List(update.groupId.value))
  )

  val groupId = UpdateHeuristic(
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

  val specific = UpdateHeuristic(
    name = "specific",
    replaceVersion = defaultReplaceVersion {
      case s: Update.Single
          if s.groupId === GroupId("org.scalameta") && s.artifactId.name === "scalafmt-core" =>
        List("version")
      case _ => List.empty
    }
  )

  def custom(customHeuristics: List[CustomHeuristic]): UpdateHeuristic = UpdateHeuristic(
    name = "custom",
    replaceVersion = defaultReplaceVersion {
      case s: Update.Single =>
        customHeuristics
          .filter(_.groupId === s.groupId)
          .filter(_.artifactName.forall(_ === s.artifactId.name))
          .flatMap(_.termsToReplace.toList)

      case Update.Group(crossDependencies, _) =>
        customHeuristics
          .filter { h =>
            crossDependencies
              .forall(_.dependencies.forall(d => h.groupId === d.groupId && h.artifactName.forall(_ === d.artifactId.name)))
          }
          .flatMap(_.termsToReplace.toList)
    }
  )

  def all(customHeuristics: List[CustomHeuristic] = Nil): Nel[UpdateHeuristic] =
    Nel.of(moduleId, strict, original, relaxed, sliding, completeGroupId, groupId, specific, custom(customHeuristics))
}




