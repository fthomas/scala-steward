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

import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

final case class RepoConfig(
    commits: CommitsConfig = CommitsConfig(),
    pullRequests: PullRequestsConfig = PullRequestsConfig(),
    updates: UpdatesConfig = UpdatesConfig(),
    updatePullRequests: Option[PullRequestUpdateStrategy] = None,
) {
  def updatePullRequestsOrDefault: PullRequestUpdateStrategy =
    updatePullRequests.getOrElse(PullRequestUpdateStrategy.default)
}

object RepoConfig {
  val empty: RepoConfig = RepoConfig()

  implicit val customConfig: Configuration =
    Configuration.default.withDefaults

  implicit val repoConfigCodec: Codec[RepoConfig] =
    deriveConfiguredCodec
}
