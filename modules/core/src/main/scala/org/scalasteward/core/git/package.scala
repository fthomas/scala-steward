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

import org.scalasteward.core.data.Update
import org.scalasteward.core.repoconfig.CommitsConfig
import org.scalasteward.core.update.show

package object git {
  def branchFor(update: Update, baseBranch: Option[Branch]): Branch =
    baseBranch
      .map(branch => Branch(s"update/${branch.name}/${update.name}-${update.nextVersion}"))
      .getOrElse(Branch(s"update/${update.name}-${update.nextVersion}"))

  def commitMsgFor(
      update: Update,
      commitsConfig: CommitsConfig,
      nonDefaultBaseBranch: Option[Branch] = None
  ): String = {
    val artifact = show.oneLiner(update)
    val defaultMessage = nonDefaultBaseBranch
      .map(branch => s"Update $artifact to ${update.nextVersion} (${branch.name})")
      .getOrElse(s"Update $artifact to ${update.nextVersion}")

    commitsConfig.messageOrDefault
      .replace("${default}", defaultMessage)
      .replace("${artifactName}", artifact)
      .replace("${currentVersion}", update.currentVersion)
      .replace("${nextVersion}", update.nextVersion)
      .replace("${branchName}", nonDefaultBaseBranch.map(_.name).getOrElse(""))
  }

  // man 7 gitrevisions:
  // When you have two commits r1 and r2 you can ask for commits that are
  // reachable from r2 excluding those that are reachable from r1 by ^r1 r2
  // and it can be written as
  //   r1..r2.
  def dotdot(r1: Branch, r2: Branch): String =
    s"${r1.name}..${r2.name}"
}
