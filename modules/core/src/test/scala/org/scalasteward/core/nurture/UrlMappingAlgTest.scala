package org.scalasteward.core.nurture

import cats.effect.IO
import org.http4s.{HttpRoutes, Uri}
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalasteward.core.TestInstances._
import org.scalasteward.core.coursier.CoursierAlg
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Scope}
import org.scalasteward.core.scaladex.{HttpScaladexClient, ScaladexAlg}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UrlMappingAlgTest extends AnyFunSuite with Matchers {

  object QueryParam extends QueryParamDecoderMatcher[String]("q")
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "search" :? QueryParam("sbt-scalatra") =>
      Ok("<h4>scalatra/sbt-scalatra</h4>")

    case GET -> Root / "search" :? QueryParam("cats-core") =>
      Ok("<h4>typelevel/cats</h4>")

    case GET -> Root / owner / repo / artifact =>
      Ok(s""" <a href="https://github.com/${owner}/${repo}">GitHub<a>
            | <p>${artifact}</p>
            |""".stripMargin)
  }

  implicit val client = Client.fromHttpApp[IO](routes.orNotFound)
  implicit val httpScaladexClient = new HttpScaladexClient[IO]
  implicit val coursierAlg = CoursierAlg.create[IO]
  implicit val scaladexAlg = ScaladexAlg.create[IO]
  val urlMappingAlg = UrlMappingAlg.create[IO]

  test("getArtifactUrl") {
    val dependencies = List(
      Dependency(
        GroupId("org.typelevel"),
        ArtifactId("cats-core", Some("cats-core_2.12")),
        "1.6.0"
      ),
      Dependency(
        GroupId("org.scalatra.sbt"),
        ArtifactId("sbt-scalatra", Some("sbt-scalatra_2.12")),
        "1.0.1"
      )
    )
    urlMappingAlg
      .getArtifactIdUrlMapping(Scope(dependencies, List.empty))
      .unsafeRunSync() shouldBe Map(
      "cats-core" -> Uri.unsafeFromString("https://github.com/typelevel/cats"),
      "sbt-scalatra" -> Uri.unsafeFromString("https://github.com/scalatra/sbt-scalatra")
    )
  }
}
