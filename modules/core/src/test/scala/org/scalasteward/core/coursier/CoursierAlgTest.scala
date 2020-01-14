package org.scalasteward.core.coursier

import org.http4s.syntax.literals._
import org.scalasteward.core.data.Resolver.MavenRepository
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Resolver}
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.sbt.data.{SbtVersion, ScalaVersion}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CoursierAlgTest extends AnyFunSuite with Matchers {
  val resolvers = List(MavenRepository("public", "https://repo1.maven.org/maven2/"))

  test("getArtifactUrl: library") {
    val dep =
      Dependency(GroupId("org.typelevel"), ArtifactId("cats-effect", "cats-effect_2.12"), "1.0.0")
    val (state, result) = coursierAlg
      .getArtifactUrl(dep, resolvers)
      .run(MockState.empty)
      .unsafeRunSync()
    state shouldBe MockState.empty
    result shouldBe Some(uri"https://github.com/typelevel/cats-effect")
  }

  test("getArtifactUrl: defaults to homepage") {
    val dep = Dependency(
      GroupId("com.typesafe.play"),
      ArtifactId("play-ws-standalone-json", "play-ws-standalone-json_2.12"),
      "2.1.0-M7"
    )
    val (state, result) = coursierAlg
      .getArtifactUrl(dep, resolvers)
      .run(MockState.empty)
      .unsafeRunSync()
    state shouldBe MockState.empty
    result shouldBe Some(uri"https://github.com/playframework/play-ws")
  }

  test("getArtifactUrl: URL with no or invalid scheme") {
    val dep1 = Dependency(
      GroupId("org.msgpack"),
      ArtifactId("msgpack-core"),
      "0.8.20"
    )
    val (state1, result1) = coursierAlg
      .getArtifactUrl(dep1, resolvers)
      .run(MockState.empty)
      .unsafeRunSync()
    state1 shouldBe MockState.empty
    result1 shouldBe Some(uri"http://msgpack.org/")

    val dep2 = Dependency(
      GroupId("org.xhtmlrenderer"),
      ArtifactId("flying-saucer-parent"),
      "9.0.1"
    )
    val (state2, result2) = coursierAlg
      .getArtifactUrl(dep2, resolvers)
      .run(MockState.empty)
      .unsafeRunSync()
    state2 shouldBe MockState.empty
    result2 shouldBe Some(uri"http://code.google.com/p/flying-saucer/")
  }

  test("getArtifactUrl: from parent") {
    val dep = Dependency(
      GroupId("net.bytebuddy"),
      ArtifactId("byte-buddy"),
      "1.10.5"
    )
    val (state, result) = coursierAlg
      .getArtifactUrl(dep, resolvers)
      .run(MockState.empty)
      .unsafeRunSync()
    state shouldBe MockState.empty
    result shouldBe Some(uri"https://bytebuddy.net")
  }

  test("getArtifactUrl: minimal pom") {
    val dep = Dependency(
      GroupId("altrmi"),
      ArtifactId("altrmi-common"),
      "0.9.6"
    )
    val (state, result) = coursierAlg
      .getArtifactUrl(dep, resolvers)
      .run(MockState.empty)
      .unsafeRunSync()
    state shouldBe MockState.empty
    result shouldBe None
  }

  test("getArtifactUrl: sbt plugin on Maven Central") {
    val dep = Dependency(
      GroupId("org.xerial.sbt"),
      ArtifactId("sbt-sonatype"),
      "3.8",
      Some(SbtVersion("1.0")),
      Some(ScalaVersion("2.12"))
    )
    val (state, result) = coursierAlg
      .getArtifactUrl(dep, resolvers)
      .run(MockState.empty)
      .unsafeRunSync()
    state shouldBe MockState.empty
    result shouldBe Some(uri"https://github.com/xerial/sbt-sonatype")
  }

  test("getArtifactUrl: sbt plugin on sbt-plugin-releases") {
    val dep = Dependency(
      GroupId("com.github.gseitz"),
      ArtifactId("sbt-release"),
      "1.0.12",
      Some(SbtVersion("1.0")),
      Some(ScalaVersion("2.12"))
    )
    val (state, result) =
      coursierAlg.getArtifactUrl(dep, resolvers).run(MockState.empty).unsafeRunSync()
    state shouldBe MockState.empty
    result shouldBe Some(uri"https://github.com/sbt/sbt-release")
  }

  test("getArtifactIdUrlMapping") {
    val dependencies = List(
      Dependency(GroupId("org.typelevel"), ArtifactId("cats-core", "cats-core_2.12"), "1.6.0"),
      Dependency(GroupId("org.typelevel"), ArtifactId("cats-effect", "cats-effect_2.12"), "1.0.0")
    )
    val (state, result) = coursierAlg
      .getArtifactIdUrlMapping(dependencies, resolvers)
      .run(MockState.empty)
      .unsafeRunSync()
    state shouldBe MockState.empty
    result shouldBe Map(
      "cats-core" -> uri"https://github.com/typelevel/cats",
      "cats-effect" -> uri"https://github.com/typelevel/cats-effect"
    )
  }
}
