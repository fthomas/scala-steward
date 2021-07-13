package org.scalasteward.core.update

import cats.effect.unsafe.implicits.global
import io.circe.parser.decode
import munit.FunSuite
import org.scalasteward.core.TestInstances.dummyRepoCache
import org.scalasteward.core.TestSyntax._
import org.scalasteward.core.data.Resolver.MavenRepository
import org.scalasteward.core.data.{DependencyInfo, RepoData, Scope}
import org.scalasteward.core.mock.MockConfig.config
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
    val initial = MockState.empty.addFiles(pullRequestsFile -> pullRequestsContent).unsafeRunSync()
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.needsAttention(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      trace = Vector(
        Log("Find updates for fthomas/scalafix-test"),
        Log("Found 0 updates"),
        Cmd("read", pullRequestsFile.toString),
        Log("fthomas/scalafix-test is up-to-date")
      )
    )
    assertEquals(state, expected)
  }

  test("needsAttention: update scala-library") {
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
          |      "includeScala" : "yes"
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
    val initial = MockState.empty
      .addFiles(pullRequestsFile -> pullRequestsContent, versionsFile -> versionsContent)
      .unsafeRunSync()
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.needsAttention(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
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
    assertEquals(state, expected)
  }

  test("needsAttention: no overtaking updates") {
    val repo = Repo("pruning-test", "repo5")
    val repoCache = dummyRepoCache.copy(dependencyInfos =
      List(
        Scope(
          List(
            DependencyInfo("org.scala-lang" % "scala-library" % "2.12.14", List("build.sbt")),
            DependencyInfo("org.scala-lang" % "scala-library" % "2.13.6", List("build.sbt"))
          ),
          List(MavenRepository("public", "https://repo5.org/maven/", None))
        )
      )
    )
    val data = RepoData(repo, repoCache, RepoConfig.empty)
    val versionsFile =
      config.workspace / "store/versions/v2/https/repo5.org/maven/org/scala-lang/scala-library/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.12.13",
          |    "2.13.5"
          |  ]
          |}""".stripMargin
    (for {
      initial <- MockState.empty.addFiles(versionsFile -> versionsContent)
      // This should not propose an update from 2.12.13 to 2.13.5.
      res <- pruningAlg.needsAttention(data).runA(initial)
      (_, updates) = res
    } yield assertEquals(updates, List.empty)).unsafeRunSync()
  }
}
