package org.scalasteward.core.bitbucket.http4s

import cats.effect.IO
import io.circe.literal._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git._
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.data._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class Http4sBitbucketApiAlgTest extends AnyFunSuite with Matchers {
  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "repositories" / "fthomas" / "base.g8" =>
      Ok(
        json"""{
          "name": "base.g8",
          "mainbranch": {
              "type": "branch",
              "name": "master"
          },
          "owner": {
              "nickname": "fthomas"
          },
          "links": {
              "clone": [
                  {
                      "href": "https://scala-steward@bitbucket.org/fthomas/base.g8.git",
                      "name": "https"
                  }
              ]
          }
        }"""
      )
    case GET -> Root / "repositories" / "scala-steward" / "base.g8" =>
      Ok(
        json"""{
          "name": "base.g8",
          "mainbranch": {
              "type": "branch",
              "name": "master"
          },
          "owner": {
              "nickname": "scala-steward"
          },
          "parent": {
              "full_name": "fthomas/base.g8"
          },
          "links": {
              "clone": [
                  {
                      "href": "https://scala-steward@bitbucket.org/scala-steward/base.g8.git",
                      "name": "https"
                  }
              ]
          }
        }"""
      )
    case GET -> Root / "repositories" / "fthomas" / "base.g8" / "refs" / "branches" / "master" =>
      Ok(
        json"""{
          "name": "master",
          "target": {
              "hash": "07eb2a203e297c8340273950e98b2cab68b560c1"
          }
        }"""
      )
    case GET -> Root / "repositories" / "fthomas" / "base.g8" / "refs" / "branches" / "selected" =>
      Ok(
        json"""{
          "name": "selected",
          "target": {
              "hash": "f782cf77b421b655fd2d52e88a39943d1c5b877d"
          }
        }"""
      )
    case POST -> Root / "repositories" / "fthomas" / "base.g8" / "forks" =>
      Ok(
        json"""{
          "name": "base.g8",
          "mainbranch": {
            "type": "branch",
            "name": "master"
          },
          "owner": {
            "nickname": "scala-steward"
          },
          "parent": {
            "full_name": "fthomas/base.g8"
          },
          "links": {
            "clone": [
                {
                    "href": "https://scala-steward@bitbucket.org/scala-steward/base.g8.git",
                    "name": "https"
                }
            ]
          }
        }"""
      )
    case POST -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" =>
      Ok(
        json"""{
            "title": "scala-steward-pr",
            "state": "OPEN",
            "links": {
                "self": {
                    "href": "https://api.bitbucket.org/2.0/repositories/fthomas/base.g8/pullrequests/2"
                }
            }
          }"""
      )
    case GET -> Root / "repositories" / "fthomas" / "base.g8" / "pullrequests" =>
      Ok(
        json"""{
          "values": [
              {
                  "title": "scala-steward-pr",
                  "state": "OPEN",
                  "links": {
                      "self": {
                          "href": "https://api.bitbucket.org/2.0/repositories/fthomas/base.g8/pullrequests/2"
                      }
                  }
              }
          ]
      }"""
      )
  }

  implicit val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
  implicit val httpJsonClient: HttpJsonClient[IO] = new HttpJsonClient[IO]
  val bitbucketApiAlg = new Http4sBitbucketApiAlg[IO](
    config.vcsApiHost,
    AuthenticatedUser("scala-steward", ""),
    _ => IO.pure,
    false
  )

  val prUrl = uri"https://api.bitbucket.org/2.0/repositories/fthomas/base.g8/pullrequests/2"
  val repo = Repo("fthomas", "base.g8")
  val master = Branch("master")
  val selected = Branch("selected")
  val parent = RepoOut(
    "base.g8",
    UserOut("fthomas"),
    None,
    uri"https://scala-steward@bitbucket.org/fthomas/base.g8.git",
    master
  )

  val fork = RepoOut(
    "base.g8",
    UserOut("scala-steward"),
    Some(parent),
    uri"https://scala-steward@bitbucket.org/scala-steward/base.g8.git",
    master
  )

  val defaultBranch = BranchOut(
    master,
    CommitOut(Sha1(HexString("07eb2a203e297c8340273950e98b2cab68b560c1")))
  )

  val selectedBranch: BranchOut = BranchOut(
    selected,
    CommitOut(Sha1(HexString("f782cf77b421b655fd2d52e88a39943d1c5b877d")))
  )

  val pullRequest = PullRequestOut(prUrl, PullRequestState.Open, "scala-steward-pr")

  test("createForkOrGetRepo") {
    val repoOut =
      bitbucketApiAlg.createForkOrGetRepo(config, repo).unsafeRunSync()
    repoOut shouldBe fork
  }

  test("createForkOrGetRepo without forking") {
    val repoOut =
      bitbucketApiAlg.createForkOrGetRepo(config.copy(doNotFork = true), repo).unsafeRunSync()
    repoOut shouldBe parent
  }

  test("createForkOrGetRepoWithBranch") {
    val (repoOut, branchOut) =
      bitbucketApiAlg.createForkOrGetRepoWithBranch(config, repo).unsafeRunSync()
    repoOut shouldBe fork
    branchOut shouldBe defaultBranch
  }

  test("createForkOrGetRepoWithBranch - selected branch") {
    val (_, branchOut) =
      bitbucketApiAlg
        .createForkOrGetRepoWithBranch(config, repo.copy(branch = Some(selectedBranch.name)))
        .unsafeRunSync()

    branchOut shouldBe selectedBranch
  }

  test("createForkOrGetRepoWithBranch without forking") {
    val (repoOut, branchOut) =
      bitbucketApiAlg
        .createForkOrGetRepoWithBranch(config.copy(doNotFork = true), repo)
        .unsafeRunSync()
    repoOut shouldBe parent
    branchOut shouldBe defaultBranch
  }

  test("createForkOrGetRepoWithBranch without forking - selected branch") {
    val (repoOut, branchOut) =
      bitbucketApiAlg
        .createForkOrGetRepoWithBranch(
          config.copy(doNotFork = true),
          repo.copy(branch = Some(selectedBranch.name))
        )
        .unsafeRunSync()
    repoOut shouldBe parent
    branchOut shouldBe selectedBranch
  }

  test("createPullRequest") {
    val data = NewPullRequestData(
      "scala-steward-pr",
      "body",
      "master",
      Branch("master")
    )
    val pr = bitbucketApiAlg.createPullRequest(repo, data).unsafeRunSync()
    pr shouldBe pullRequest
  }

  test("createPullRequest - selected branch") {
    val data = NewPullRequestData(
      "scala-steward-pr",
      "body",
      "selected",
      Branch("selected")
    )
    val pr =
      bitbucketApiAlg.createPullRequest(repo.copy(branch = Some(selected)), data).unsafeRunSync()
    pr shouldBe pullRequest
  }

  test("listPullRequests") {
    val prs = bitbucketApiAlg.listPullRequests(repo, "master", master).unsafeRunSync()
    (prs should contain).only(pullRequest)
  }
}
