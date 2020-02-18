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

package org.scalasteward.core.repoconfig

import io.circe.{Decoder, Encoder}
import org.scalasteward.core.repoconfig.PullRequestFrequency._
import org.scalasteward.core.util.Timestamp
import scala.concurrent.duration._

sealed trait PullRequestFrequency {
  def render: String

  def elapsed(t1: Timestamp, t2: Timestamp): Boolean =
    this match {
      case Asap    => true
      case Daily   => t1.until(t2) >= 1.day
      case Weekly  => t1.until(t2) >= 7.days
      case Monthly => t1.until(t2) >= 30.days
    }
}

object PullRequestFrequency {
  case object Asap extends PullRequestFrequency { val render = "@asap" }
  case object Daily extends PullRequestFrequency { val render = "@daily" }
  case object Weekly extends PullRequestFrequency { val render = "@weekly" }
  case object Monthly extends PullRequestFrequency { val render = "@monthly" }

  val default: PullRequestFrequency = Asap

  def fromString(s: String): PullRequestFrequency =
    s.trim.toLowerCase match {
      case Asap.render    => Asap
      case Daily.render   => Daily
      case Weekly.render  => Weekly
      case Monthly.render => Monthly
      case _              => default
    }

  implicit val pullRequestFrequencyDecoder: Decoder[PullRequestFrequency] =
    Decoder[String].map(fromString)

  implicit val pullRequestFrequencyEncoder: Encoder[PullRequestFrequency] =
    Encoder[String].contramap(_.render)
}
