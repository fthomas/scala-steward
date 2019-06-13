/*
 * Copyright 2018-2019 scala-steward contributors
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

package org.scalasteward.core.application

import caseapp._
import caseapp.core.Error.MalformedValue
import caseapp.core.argparser.ArgParser
import caseapp.core.argparser.SimpleArgParser
import cats.implicits._
import org.http4s.Uri
import org.scalasteward.core.util.ApplicativeThrowable

trait Cli[F[_]] {
  def parseArgs(args: List[String]): F[Cli.Args]
}

object Cli {
  final case class Args(
      workspace: String,
      reposFile: String,
      gitAuthorName: String,
      gitAuthorEmail: String,
      githubApiHost: Uri = Uri.uri("https://api.github.com"),
      githubLogin: String,
      gitAskPass: String,
      signCommits: Boolean = false,
      whitelist: List[String] = Nil,
      readOnly: List[String] = Nil,
      disableSandbox: Boolean = false,
      doNotFork: Boolean = false,
      ignoreOptsFiles: Boolean = false,
      keepCredentials: Boolean = false,
      envVar: List[EnvVar] = Nil
  )
  final case class EnvVar(name: String, value: String)
  implicit val envVarParser: SimpleArgParser[EnvVar] =
    SimpleArgParser.from[EnvVar]("env-var") { s =>
      s.trim.split('=').toList match {
        case name :: value :: Nil =>
          Right(EnvVar(name.trim, value.trim))
        case _ =>
          Left(
            core.Error.MalformedValue(
              "env-var",
              "The value is expected in the following format: NAME=VALUE."
            )
          )
      }
    }

  def create[F[_]](implicit F: ApplicativeThrowable[F]): Cli[F] = new Cli[F] {
    override def parseArgs(args: List[String]): F[Args] =
      F.fromEither {
        CaseApp
          .parse[Args](args)
          .bimap(e => new Throwable(e.message), { case (parsed, _) => parsed })
      }
  }

  implicit val uriArgParser: ArgParser[Uri] =
    ArgParser[String].xmapError(
      _.renderString,
      s => Uri.fromString(s).leftMap(pf => MalformedValue("Uri", pf.message))
    )
}
