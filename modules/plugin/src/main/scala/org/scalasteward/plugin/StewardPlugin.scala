/*
 * Copyright 2018-2019 Scala Steward contributors
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

package org.scalasteward.plugin

import com.timushev.sbt.updates.UpdatesKeys.dependencyUpdatesData
import com.timushev.sbt.updates.versions.{InvalidVersion, ValidVersion}
import sbt.Keys._
import sbt._

object StewardPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val stewardDependencies =
      taskKey[Unit]("Prints dependencies as JSON for consumption by Scala Steward.")
    val stewardUpdates =
      taskKey[Unit]("Prints dependency updates as JSON for consumption by Scala Steward.")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    stewardDependencies := {
      val log = streams.value.log
      val sourcePositions = dependencyPositions.value
      val scalaBinaryVersionValue = scalaBinaryVersion.value
      val scalaVersionValue = scalaVersion.value

      val dependencies = libraryDependencies.value
        .filter(isDefinedInBuildFiles(_, sourcePositions))
        .map(moduleId => toDependency(moduleId, scalaVersionValue, scalaBinaryVersionValue))

      dependencies.map(_.asJson).foreach(s => log.info(s))
    },
    stewardUpdates := {
      val log = streams.value.log
      val scalaBinaryVersionValue = scalaBinaryVersion.value
      val scalaVersionValue = scalaVersion.value

      val updates = dependencyUpdatesData.value.toList.map {
        case (moduleId, versions) =>
          RawUpdate(
            dependency = toDependency(moduleId, scalaVersionValue, scalaBinaryVersionValue),
            newerVersions = versions.toList.map {
              case v: ValidVersion   => v.text
              case v: InvalidVersion => v.text
            }
          )
      }

      updates.map(_.asJson).foreach(s => log.info(s))
    }
  )

  // Inspired by https://github.com/rtimush/sbt-updates/issues/42
  private def isDefinedInBuildFiles(
      moduleId: ModuleID,
      sourcePositions: Map[ModuleID, SourcePosition]
  ): Boolean =
    sourcePositions.get(moduleId) match {
      case Some(fp: FilePosition) if fp.path.startsWith("(sbt.Classpaths") => true
      case Some(fp: FilePosition) if fp.path.startsWith("(")               => false
      case Some(fp: FilePosition)
          if fp.path.startsWith("Defaults.scala")
            && !moduleId.configurations.exists(_ == "plugin->default(compile)") =>
        false
      case _ => true
    }

  private def crossName(
      moduleId: ModuleID,
      scalaVersion: String,
      scalaBinaryVersion: String
  ): Option[String] =
    CrossVersion(moduleId.crossVersion, scalaVersion, scalaBinaryVersion).map(_(moduleId.name))

  private def toDependency(
      moduleId: ModuleID,
      scalaVersion: String,
      scalaBinaryVersion: String
  ): Dependency =
    Dependency(
      groupId = moduleId.organization,
      artifactId =
        ArtifactId(moduleId.name, crossName(moduleId, scalaVersion, scalaBinaryVersion).toList),
      version = moduleId.revision,
      configurations = moduleId.configurations,
      sbtVersion = moduleId.extraAttributes.get("e:sbtVersion"),
      scalaVersion = moduleId.extraAttributes.get("e:scalaVersion")
    )

  final private case class ArtifactId(
      name: String,
      crossNames: List[String]
  ) {
    def asJson: String =
      objToJson(
        List(
          "name" -> strToJson(name),
          "crossNames" -> seqToJson(crossNames.map(strToJson))
        )
      )
  }

  final private case class Dependency(
      groupId: String,
      artifactId: ArtifactId,
      version: String,
      configurations: Option[String],
      sbtVersion: Option[String],
      scalaVersion: Option[String]
  ) {
    def asJson: String =
      objToJson(
        List(
          "groupId" -> strToJson(groupId),
          "artifactId" -> artifactId.asJson,
          "version" -> strToJson(version),
          "configurations" -> optToJson(configurations.map(strToJson)),
          "sbtVersion" -> optToJson(sbtVersion.map(strToJson)),
          "scalaVersion" -> optToJson(scalaVersion.map(strToJson))
        )
      )
  }

  final private case class RawUpdate(
      dependency: Dependency,
      newerVersions: List[String]
  ) {
    def asJson: String =
      objToJson(
        List(
          "dependency" -> dependency.asJson,
          "newerVersions" -> seqToJson(newerVersions.map(strToJson))
        )
      )
  }

  private def strToJson(str: String): String =
    s""""$str""""

  private def optToJson(opt: Option[String]): String =
    opt.getOrElse("null")

  private def seqToJson(seq: Seq[String]): String =
    seq.mkString("[ ", ", ", " ]")

  private def objToJson(obj: List[(String, String)]): String =
    obj.map { case (k, v) => s""""$k": $v""" }.mkString("{ ", ", ", " }")
}
