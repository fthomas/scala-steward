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

package org.scalasteward.core

import cats.effect.IO
import cats.implicits._
import org.scalasteward.core.data.{ArtifactId, Dependency, GroupId, Version}
import org.scalasteward.core.io.FileData
import org.scalasteward.core.sbt.data.SbtVersion

package object sbt {
  val defaultScalaBinaryVersion: String =
    BuildInfo.scalaBinaryVersion

  def sbtDependency(sbtVersion: SbtVersion): Option[Dependency] =
    if (sbtVersion.toVersion >= Version("1.0.0"))
      Some(
        Dependency(
          GroupId("org.scala-sbt"),
          ArtifactId("sbt"),
          sbtVersion.value
        )
      )
    else
      None

  val scalaStewardScalafixSbt: FileData =
    FileData(
      "scala-steward-scalafix.sbt",
      """addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.11")"""
    )

  val stewardPlugin: FileData = {
    val name = "StewardPlugin.scala"
    // I don't consider reading a resource as side-effect,
    // so it is OK to call `unsafeRunSync` here.
    io.readResource[IO](s"org/scalasteward/plugin/$name")
      .map(content => FileData(name, content))
      .unsafeRunSync()
  }
}
