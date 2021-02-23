package org.scalasteward.core.update

import io.circe.parser.decode
import munit.FunSuite
import org.scalasteward.core.data.RepoData
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockContext.context.pruningAlg
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.mock.MockState.TraceEntry.{Cmd, Log}
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.vcs.data.Repo

class PruningAlgTest extends FunSuite {
  test("needsAttention") {
    val repo = Repo("fthomas", "scalafix-test")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos": [],
          |  "maybeRepoConfig": {
          |    "pullRequests": {
          |      "frequency": "@daily"
          |    }
          |  }
          |}""".stripMargin
    )
    val pullRequestsFile =
      config.workspace / "store/pull_requests/v2/github/fthomas/scalafix-test/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/fthomas/scalafix-test/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "Single" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "org.scalatest",
          |            "artifactId" : {
          |              "name" : "scalatest",
          |              "maybeCrossName" : "scalatest_2.12"
          |            },
          |            "version" : "3.0.8",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "3.1.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : 1581969227183
          |  }
          |}""".stripMargin
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    (for {
      initial <- MockState.empty.add(pullRequestsFile, pullRequestsContent).init
      obtained <- pruningAlg.needsAttention(data).runS(initial)
      expected = initial.copy(
        trace = Vector(
          Log("Find updates for fthomas/scalafix-test"),
          Log("Found 0 updates"),
          Cmd("read", pullRequestsFile.toString),
          Log("fthomas/scalafix-test is up-to-date")
        )
      )
    } yield assertEquals(obtained, expected)).unsafeRunSync()
  }

  test("needsAttention: 0 updates when includeScala not specified in repo config") {
    val repo = Repo("pruning-test", "repo2")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos" : [
          |    {
          |      "value" : [
          |        {
          |          "dependency" : {
          |            "groupId" : "org.scala-lang",
          |            "artifactId" : {
          |              "name" : "scala-library",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.12.10",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          },
          |          "filesContainingVersion" : [
          |            "build.sbt"
          |          ]
          |        }
          |      ],
          |      "resolvers" : [
          |        {
          |          "MavenRepository" : {
          |            "name" : "public",
          |            "location" : "https://foobar.org/maven2/"
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  "maybeRepoConfig": {
          |    "pullRequests": {
          |      "frequency": "@daily"
          |    }
          |  }
          |}""".stripMargin
    )
    val pullRequestsFile =
      config.workspace / s"store/pull_requests/v2/github/${repo.show}/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/${repo.show}/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "Single" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "org.scalatest",
          |            "artifactId" : {
          |              "name" : "scalatest",
          |              "maybeCrossName" : "scalatest_2.12"
          |            },
          |            "version" : "3.0.8",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "3.1.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : 1581969227183
          |  }
          |}""".stripMargin
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    (for {
      initial <- MockState.empty.add(pullRequestsFile, pullRequestsContent).init
      obtained <- pruningAlg.needsAttention(data).runS(initial)
      expected = initial.copy(
        trace = Vector(
          Log(s"Find updates for ${repo.show}"),
          Log("Found 0 updates"),
          Cmd("read", pullRequestsFile.toString),
          Log(s"${repo.show} is up-to-date")
        )
      )
    } yield assertEquals(obtained, expected)).unsafeRunSync()
  }

  test("needsAttention: update scala-library when includeScala=true in repo config") {
    val repo = Repo("pruning-test", "repo3")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos" : [
          |    {
          |      "value" : [
          |        {
          |          "dependency" : {
          |            "groupId" : "org.scala-lang",
          |            "artifactId" : {
          |              "name" : "scala-library",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.12.10",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          },
          |          "filesContainingVersion" : [
          |            "build.sbt"
          |          ]
          |        }
          |      ],
          |      "resolvers" : [
          |        {
          |          "MavenRepository" : {
          |            "name" : "public",
          |            "location" : "https://foobar.org/maven2/"
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  "maybeRepoConfig": {
          |    "updates" : {
          |      "includeScala" : true
          |    }
          |  }
          |}""".stripMargin
    )
    val pullRequestsFile =
      config.workspace / s"store/pull_requests/v2/github/${repo.show}/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/${repo.show}/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "Single" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "org.scalatest",
          |            "artifactId" : {
          |              "name" : "scalatest",
          |              "maybeCrossName" : "scalatest_2.12"
          |            },
          |            "version" : "3.0.8",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "3.1.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : 1581969227183
          |  }
          |}""".stripMargin
    val versionsFile =
      config.workspace / "store/versions/v2/https/foobar.org/maven2/org/scala-lang/scala-library/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.12.9",
          |    "2.12.10",
          |    "2.12.11",
          |    "2.13.0",
          |    "2.13.1"
          |  ]
          |}
          |""".stripMargin
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    (for {
      initial <- MockState.empty
        .add(pullRequestsFile, pullRequestsContent)
        .add(versionsFile, versionsContent)
        .init
      obtained <- pruningAlg.needsAttention(data).runS(initial)
      expected = initial.copy(
        trace = Vector(
          Log(s"Find updates for ${repo.show}"),
          Cmd("read", versionsFile.toString),
          Cmd("read", pullRequestsFile.toString),
          Cmd("read", versionsFile.toString),
          Log("Found 1 update:\n  org.scala-lang:scala-library : 2.12.10 -> 2.12.11"),
          Log(
            s"${repo.show} is outdated:\n  new version: org.scala-lang:scala-library : 2.12.10 -> 2.12.11"
          )
        )
      )
    } yield assertEquals(obtained, expected)).unsafeRunSync()
  }
}
