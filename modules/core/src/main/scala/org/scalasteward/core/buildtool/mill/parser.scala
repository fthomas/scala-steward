/*
 * Copyright 2018-2021 Scala Steward contributors
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

package org.scalasteward.core.buildtool.mill

import cats.syntax.all._
import io.circe.{Decoder, DecodingFailure}
import org.scalasteward.core.data.{Dependency, Resolver}

object parser {
  sealed trait ParseError extends RuntimeException {
    def message: String

    final override def getMessage: String = message
  }
  case class CirceParseError(message: String, cause: io.circe.Error) extends ParseError {
    final override def getCause: Throwable = cause
  }

  def parseModules[F[_]](
      input: String
  ): Either[ParseError, List[MillModule]] =
    for {
      json <-
        io.circe.parser
          .decode[Modules](input)
          .leftMap(CirceParseError("Failed to decode Modules", _): ParseError)
    } yield json.modules

  def parseMillVersion(s: String): Option[String] =
    Option(s.trim()).filter(_.nonEmpty)

}

case class Modules(modules: List[MillModule])
object Modules {
  implicit val decoder: Decoder[Modules] = Decoder.forProduct1("modules")(apply)
}

case class MillModule(name: String, repositories: List[Resolver], dependencies: List[Dependency])

object MillModule {
  val resolverDecoder: Decoder[Resolver] = Decoder.instance { c =>
    c.downField("type").as[String].flatMap {
      case "maven" =>
        for {
          url <- c.downField("url").as[String]
          creds <- c.downField("auth").as[Option[Resolver.Credentials]]
        } yield Resolver.MavenRepository(url, url, creds)
      case "ivy" =>
        for {
          url <- c.downField("pattern").as[String]
          creds <- c.downField("auth").as[Option[Resolver.Credentials]]
        } yield Resolver.IvyRepository(url, url, creds)
      case typ => Left(DecodingFailure(s"Not a matching resolver type, $typ", c.history))
    }
  }

  implicit val decoder: Decoder[MillModule] = Decoder.instance(c =>
    for {
      name <- c.downField("name").as[String]
      resolvers <- c.downField("repositories").as(Decoder.decodeList(resolverDecoder))
      dependencies <- c.downField("dependencies").as[List[Dependency]]
    } yield MillModule(name, resolvers, dependencies)
  )
}
