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

import cats._
import cats.effect.Bracket
import cats.syntax.all._
import fs2.Pipe
import scala.collection.mutable.ListBuffer

package object util {
  final type Nel[+A] = cats.data.NonEmptyList[A]
  final val Nel = cats.data.NonEmptyList

  type ApplicativeThrow[F[_]] = ApplicativeError[F, Throwable]

  type MonadThrow[F[_]] = MonadError[F, Throwable]

  type BracketThrow[F[_]] = Bracket[F, Throwable]

  /** Appends `elem` to `buffer` such that its size does not exceed `maxSize`. */
  def appendBounded[A](buffer: ListBuffer[A], elem: A, maxSize: Int): Unit = {
    if (buffer.size >= maxSize) buffer.remove(0, maxSize / 2)
    buffer.append(elem)
  }

  /** Binds the elements of `gfb` until the first `F[Boolean]` that
    * evaluates to `true`.
    *
    * @example {{{
    * scala> import cats.data.State
    *
    * scala> bindUntilTrue(Nel.of(
    *      |   State((l: List[Int]) => (l :+ 1, false)),
    *      |   State((l: List[Int]) => (l :+ 2, true )),
    *      |   State((l: List[Int]) => (l :+ 3, false)),
    *      |   State((l: List[Int]) => (l :+ 4, true ))
    *      | )).runS(List(0)).value
    * res1: List[Int] = List(0, 1, 2)
    * }}}
    */
  def bindUntilTrue[G[_]: Foldable, F[_]: Monad](gfb: G[F[Boolean]]): F[Boolean] =
    gfb.existsM(identity)

  /** Like `Semigroup[Option[A]].combine` but allows to specify how `A`s are combined. */
  def combineOptions[A](x: Option[A], y: Option[A])(f: (A, A) => A): Option[A] =
    x match {
      case None => y
      case Some(xv) =>
        y match {
          case None     => x
          case Some(yv) => Some(f(xv, yv))
        }
    }

  /** Returns true if there is an element that is both in `fa` and `ga`. */
  def intersects[F[_]: UnorderedFoldable, G[_]: UnorderedFoldable, A: Eq](
      fa: F[A],
      ga: G[A]
  ): Boolean =
    fa.exists(a => ga.exists(b => a === b))

  /** Adds a weight to each element and cuts the stream when the total
    * weight is greater or equal to `limit`.
    *
    * @example {{{
    * scala> fs2.Stream.emits("Hello, world!").through(takeUntil(3) {
    *      |   case 'a' | 'e' | 'i' | 'o' | 'u' => 1
    *      |   case _                           => 0
    *      | }).toList.mkString
    * res1: String = Hello, wo
    * }}}
    */
  def takeUntil[F[_], A, N](limit: N)(weight: A => N)(implicit N: Numeric[N]): Pipe[F, A, A] = {
    import N._
    _.map(a => (a, weight(a)))
      .scan1[(A, N)] { case ((_, total), (a, i)) => (a, total + i) }
      .takeThrough { case (_, total) => total < limit }
      .map { case (a, _) => a }
  }

  def combineMapLastWin[K, V](first: Map[K, V], last: Map[K, V]): Map[K, V] =
    (first.view.filterKeys(key => !last.contains(key)) ++ last).toMap

}
