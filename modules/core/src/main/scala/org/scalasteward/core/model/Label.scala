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

package org.scalasteward.core.model
import io.circe.{Decoder, Encoder}

sealed trait Label {
  def name: String
}

object Label {
  final case object LibraryUpdate extends Label {
    val name = "library-update"
  }

  final case object TestLibraryUpdate extends Label {
    val name = "test-library-update"
  }

  final case object SbtPluginUpdate extends Label {
    val name = "sbt-plugin-update"
  }

  implicit val labelEncoder: Encoder[Label] =
    io.circe.generic.semiauto.deriveEncoder

  implicit val labelDecoder: Decoder[Label] =
    io.circe.generic.semiauto.deriveDecoder
}
