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

package object scaladex {

  def toSearchUrl(artifactId: String): String =
    s"https://index.scala-lang.org/search?q=${artifactId}"

  def toProjectUrl(owner: String, repoName: String, artifactId: String): String =
    s"https://index.scala-lang.org/${owner}/${repoName}/${artifactId}"

  def toGithubUrl(owner: String, repoName: String): String =
    s"https://github.com/${owner}/${repoName}"

}
