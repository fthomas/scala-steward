package org.scalasteward.core.scaladex

import cats.effect.IO
import org.http4s.{HttpRoutes, Uri}
import org.http4s.client.Client
import org.http4s.dsl.io.{->, /, :?, Ok, Root, _}
import org.http4s.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ScaladexAlgTest extends AnyFunSuite with Matchers {

  object QueryParam extends QueryParamDecoderMatcher[String]("q")

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "search" :? QueryParam(artifactId) if !artifactId.contains("NOT-FOUND") =>
      Ok(s""" <h4>owner-foo/repo-bar</h4>
            | scaladex
            | """.stripMargin)

    case GET -> Root / owner / repo / artifact =>
      Ok(s""" <a href="https://github.com/${owner}/${repo}">GitHub<a>
            | <p>${artifact}</p>
            |""".stripMargin)
  }

  implicit val client = Client.fromHttpApp[IO](routes.orNotFound)
  implicit val httpExistenceClient = new HttpScaladexClient[IO]

  test("searchProject should return Scaladex project URL of first project in search") {
    httpExistenceClient
      .searchProject("artifact")
      .unsafeRunSync() shouldBe Some("https://index.scala-lang.org/owner-foo/repo-bar/artifact")

    httpExistenceClient
      .searchProject("NOT-FOUND")
      .unsafeRunSync() shouldBe None
  }

  test("findGitHubUrl should return GitHub URL extracted from Scaladex project page") {
    httpExistenceClient
      .findGitHubUrl("artifact")
      .unsafeRunSync() shouldBe Some(Uri.unsafeFromString("https://github.com/owner-foo/repo-bar"))

    httpExistenceClient
      .searchProject("NOT-FOUND")
      .unsafeRunSync() shouldBe None
  }
}
