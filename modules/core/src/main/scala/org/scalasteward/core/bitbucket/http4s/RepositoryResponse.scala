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

package org.scalasteward.core.bitbucket.http4s

import cats.syntax.all._
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import org.http4s.Uri
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.uri._
import org.scalasteward.core.vcs.data.{Repo, UserOut}
import scala.annotation.tailrec

final private[http4s] case class RepositoryResponse(
    name: String,
    mainBranch: Branch,
    owner: UserOut,
    httpsCloneUrl: Uri,
    parent: Option[Repo]
)

private[http4s] object RepositoryResponse {
  implicit private val repoDecoder: Decoder[Repo] = Decoder.instance { c =>
    c.as[String].map(_.split('/')).flatMap { parts =>
      parts match {
        case Array(owner, name)         => Repo(owner, name, None).asRight
        case Array(owner, name, branch) => Repo(owner, name, Some(Branch(branch))).asRight
        case _                          => DecodingFailure("Repo", c.history).asLeft
      }
    }
  }

  implicit val decoder: Decoder[RepositoryResponse] = Decoder.instance { c =>
    for {
      name <- c.downField("name").as[String]
      owner <-
        c.downField("owner")
          .downField("username")
          .as[String]
          .orElse(c.downField("owner").downField("nickname").as[String])
      cloneUrl <-
        c.downField("links")
          .downField("clone")
          .downAt { p =>
            p.asObject
              .flatMap(o => o("name"))
              .flatMap(_.asString)
              .contains("https")
          }
          .downField("href")
          .as[Uri]
      defaultBranch <- c.downField("mainbranch").downField("name").as[Branch]
      maybeParent <- c.downField("parent").downField("full_name").as[Option[Repo]]
    } yield RepositoryResponse(name, defaultBranch, UserOut(owner), cloneUrl, maybeParent)
  }

  /**
    * Monkey patches the [[io.circe.ACursor]] class to get the `downAt` function back, which was removed in
    * version 0.12.0-M4.
    *
    * @see https://gitter.im/circe/circe?at=5d3f71eff0ff3e2bba8ece73
    * @param cursor The cursor to patch.
    */
  implicit class RichACursor(cursor: ACursor) {

    /**
      * If the focus is a JSON array, move to the first element that satisfies the given predicate.
      */
    def downAt(p: Json => Boolean): ACursor = {
      @tailrec
      def find(c: ACursor): ACursor =
        if (c.succeeded)
          if (c.focus.exists(p)) c else find(c.right)
        else c

      find(cursor.downArray)
    }
  }
}
