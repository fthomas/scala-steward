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

package org.scalasteward.core.vcs.data

import io.circe.{KeyDecoder, KeyEncoder}
import org.scalasteward.core.git.Branch

final case class Repo(
    owner: String,
    repo: String,
    branch: Option[Branch] = None
) {
  def show: String = branch.map(branch => s"$owner/$repo/${branch.name}").getOrElse(s"$owner/$repo")
}

object Repo {
  implicit val repoKeyDecoder: KeyDecoder[Repo] = {
    val regex = s"(.+)/([^#/\n]+)(?:#(.+))?".r
    KeyDecoder.instance {
      case regex(owner, repo, null)   => Some(Repo(owner, repo, None))
      case regex(owner, repo, branch) => Some(Repo(owner, repo, Some(Branch(branch))))
      case _                          => None
    }
  }

  implicit val repoKeyEncoder: KeyEncoder[Repo] =
    KeyEncoder.instance(repo =>
      repo.branch
        .map(branch => s"${repo.owner}/${repo.repo}/${branch.name}")
        .getOrElse(s"${repo.owner}/${repo.repo}")
    )
}
