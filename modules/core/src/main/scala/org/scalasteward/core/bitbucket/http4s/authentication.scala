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

package org.scalasteward.core.bitbucket.http4s

import cats.Applicative
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Request}
import org.scalasteward.core.vcs.data.AuthenticatedUser

object authentication {
  def addCredentials[F[_]: Applicative](user: AuthenticatedUser): Request[F] => F[Request[F]] =
    _.putHeaders(Authorization(BasicCredentials(user.login, user.accessToken))).pure[F]
}
