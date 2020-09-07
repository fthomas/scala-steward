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

import cats.effect.Sync
import cats.syntax.all._
import org.http4s.{Request, Status, Uri}
import org.scalasteward.core.bitbucket.Url
import org.scalasteward.core.bitbucket.http4s.json._
import org.scalasteward.core.git.Branch
import org.scalasteward.core.util.{HttpJsonClient, UnexpectedResponse}
import org.scalasteward.core.vcs.VCSApiAlg
import org.scalasteward.core.vcs.data._

class Http4sBitbucketApiAlg[F[_]: Sync](
    bitbucketApiHost: Uri,
    user: AuthenticatedUser,
    modify: Repo => Request[F] => F[Request[F]],
    doNotFork: Boolean
)(implicit client: HttpJsonClient[F])
    extends VCSApiAlg[F] {
  private val url = new Url(bitbucketApiHost)

  override def createFork(repo: Repo): F[RepoOut] =
    for {
      fork <- client.post[RepositoryResponse](url.forks(repo), modify(repo)).recoverWith {
        case UnexpectedResponse(_, _, _, Status.BadRequest, _) =>
          client.get(url.repo(repo.copy(owner = user.login)), modify(repo))
      }
      maybeParent <-
        fork.parent
          .map(n => client.get[RepositoryResponse](url.repo(n), modify(n)))
          .sequence[F, RepositoryResponse]
    } yield mapToRepoOut(fork, maybeParent)

  private def mapToRepoOut(
      repo: RepositoryResponse,
      maybeParent: Option[RepositoryResponse]
  ): RepoOut =
    RepoOut(
      repo.name,
      repo.owner,
      maybeParent.map(p => mapToRepoOut(p, None)),
      repo.httpsCloneUrl,
      repo.mainBranch
    )

  override def createPullRequest(repo: Repo, data: NewPullRequestData): F[PullRequestOut] = {
    val sourceBranchOwner = if (doNotFork) repo.owner else user.login

    val payload = CreatePullRequestRequest(
      data.title,
      Branch(data.head),
      Repo(sourceBranchOwner, repo.repo, repo.branch),
      data.base,
      data.body
    )
    client.postWithBody(url.pullRequests(repo), payload, modify(repo))
  }

  override def getBranch(repo: Repo, branch: Branch): F[BranchOut] =
    client.get(url.branch(repo, branch), modify(repo))

  override def getRepo(repo: Repo): F[RepoOut] =
    for {
      repo <- client.get[RepositoryResponse](url.repo(repo), modify(repo))
      maybeParent <-
        repo.parent
          .map(n => client.get[RepositoryResponse](url.repo(n), modify(n)))
          .sequence[F, RepositoryResponse]
    } yield mapToRepoOut(repo, maybeParent)

  override def listPullRequests(repo: Repo, head: String, base: Branch): F[List[PullRequestOut]] =
    client
      .get[Page[PullRequestOut]](url.listPullRequests(repo, head), modify(repo))
      .map(_.values)
}
