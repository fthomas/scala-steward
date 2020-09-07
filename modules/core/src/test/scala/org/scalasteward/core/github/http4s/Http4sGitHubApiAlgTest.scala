package org.scalasteward.core.github.http4s

import cats.effect.IO
import io.circe.literal._
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.git.Sha1.HexString
import org.scalasteward.core.git.{Branch, Sha1}
import org.scalasteward.core.mock.MockContext.config
import org.scalasteward.core.util.HttpJsonClient
import org.scalasteward.core.vcs.data._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class Http4sGitHubApiAlgTest extends AnyFunSuite with Matchers {
  val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "repos" / "fthomas" / "base.g8" =>
        Ok(
          json""" {
            "name": "base.g8",
            "owner": { "login": "fthomas" },
            "clone_url": "https://github.com/fthomas/base.g8.git",
            "default_branch": "master"
          } """
        )

      case GET -> Root / "repos" / "fthomas" / "base.g8" / "branches" / "master" =>
        Ok(
          json""" {
            "name": "master",
            "commit": { "sha": "07eb2a203e297c8340273950e98b2cab68b560c1" }
          } """
        )

      case GET -> Root / "repos" / "fthomas" / "base.g8" / "branches" / "selected" =>
        Ok(
          json""" {
            "name": "selected",
            "commit": { "sha": "f782cf77b421b655fd2d52e88a39943d1c5b877d" }
          } """
        )

      case POST -> Root / "repos" / "fthomas" / "base.g8" / "forks" =>
        Ok(
          json""" {
            "name": "base.g8-1",
            "owner": { "login": "scala-steward" },
            "parent": {
              "name": "base.g8",
              "owner": { "login": "fthomas" },
              "clone_url": "https://github.com/fthomas/base.g8.git",
              "default_branch": "master"
            },
            "clone_url": "https://github.com/scala-steward/base.g8-1.git",
            "default_branch": "master"
          } """
        )

      case req =>
        println(req.toString())
        NotFound()
    }

  implicit val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
  implicit val httpJsonClient: HttpJsonClient[IO] = new HttpJsonClient[IO]
  val gitHubApiAlg = new Http4sGitHubApiAlg[IO](config.vcsApiHost, _ => IO.pure)

  val repo = Repo("fthomas", "base.g8")

  val parent = RepoOut(
    "base.g8",
    UserOut("fthomas"),
    None,
    uri"https://github.com/fthomas/base.g8.git",
    Branch("master")
  )

  val fork = RepoOut(
    "base.g8-1",
    UserOut("scala-steward"),
    Some(parent),
    uri"https://github.com/scala-steward/base.g8-1.git",
    Branch("master")
  )

  val defaultBranch = BranchOut(
    Branch("master"),
    CommitOut(Sha1(HexString("07eb2a203e297c8340273950e98b2cab68b560c1")))
  )

  val selectedBranch = BranchOut(
    Branch("selected"),
    CommitOut(Sha1(HexString("f782cf77b421b655fd2d52e88a39943d1c5b877d")))
  )

  test("createForkOrGetRepo") {
    val repoOut =
      gitHubApiAlg.createForkOrGetRepo(config, repo).unsafeRunSync()
    repoOut shouldBe fork
  }

  test("createForkOrGetRepo without forking") {
    val repoOut =
      gitHubApiAlg.createForkOrGetRepo(config.copy(doNotFork = true), repo).unsafeRunSync()
    repoOut shouldBe parent
  }

  test("createForkOrGetRepoWithBranch") {
    val (repoOut, branchOut) =
      gitHubApiAlg.createForkOrGetRepoWithBranch(config, repo).unsafeRunSync()
    repoOut shouldBe fork
    branchOut shouldBe defaultBranch
  }

  test("createForkOrGetRepoWithBranch - selected branch") {
    val (repoOut, branchOut) =
      gitHubApiAlg
        .createForkOrGetRepoWithBranch(config, repo.copy(branch = Some(selectedBranch.name)))
        .unsafeRunSync()
    repoOut shouldBe fork
    branchOut shouldBe selectedBranch
  }

  test("createForkOrGetRepoWithDefaultBranch without forking") {
    val (repoOut, branchOut) =
      gitHubApiAlg
        .createForkOrGetRepoWithBranch(config.copy(doNotFork = true), repo)
        .unsafeRunSync()
    repoOut shouldBe parent
    branchOut shouldBe defaultBranch
  }

  test("createForkOrGetRepoWithBranch without forking - selected branch") {
    val (repoOut, branchOut) =
      gitHubApiAlg
        .createForkOrGetRepoWithBranch(
          config.copy(doNotFork = true),
          repo.copy(branch = Some(selectedBranch.name))
        )
        .unsafeRunSync()
    repoOut shouldBe parent
    branchOut shouldBe selectedBranch
  }
}
