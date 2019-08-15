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

package org.scalasteward.core.scaladex

import cats.effect.Sync
import cats.implicits._
import org.http4s.Uri
import org.http4s.client.Client

import scala.util.matching.Regex

final class HttpScaladexClient[F[_]](
    implicit
    client: Client[F],
    F: Sync[F]
) {
  private val projectTitleFragment = "<h4>([\\w-]+)/([\\w-]+)</h4>".r
  private val githubUrlFragment = "<a href=\"https://github\\.com/([\\w-]+)/([\\w-]+)/?\"".r

  def searchProject(artifactId: String): F[Option[String]] =
    extractFromPage(toSearchUrl(artifactId), projectTitleFragment) { matched =>
      toProjectUrl(matched.group(1), matched.group(2), artifactId)
    }

  def findGitHubUrl(artifactId: String): F[Option[String]] =
    for {
      maybeProjectUrl <- searchProject(artifactId)
      maybeGitHubUrl <- maybeProjectUrl.fold(F.pure(Option.empty[String])) { projectUrl =>
        extractFromPage(projectUrl, githubUrlFragment) { matched =>
          toGithubUrl(matched.group(1), matched.group(2))
        }
      }
    } yield maybeGitHubUrl

  private def extractFromPage(url: String, regex: Regex)(
      matched: Regex.Match => String
  ): F[Option[String]] =
    for {
      url <- F.fromEither(Uri.fromString(url))
      maybeExtracted <- client.get(url) { response =>
        for {
          contents <- response.body.through(fs2.text.utf8Decode).compile.foldMonoid
        } yield regex.findFirstMatchIn(contents).map(matched)
      }
    } yield maybeExtracted

}
