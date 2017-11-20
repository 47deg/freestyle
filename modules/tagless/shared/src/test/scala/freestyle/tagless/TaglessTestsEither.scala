/*
 * Copyright 2017 47 Degrees, LLC. <http://www.47deg.com>
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

package freestyle.tagless
package puretestImpl

import org.hablapps.puretest._
import cats.~>
import cats.instances.either._

class TaglessTestsEither extends TaglessTests.ScalaTest[TaglessTestsEither.P](
  TaglessTestsEither.a1,
  TaglessTestsEither.a2,
  TaglessTestsEither.a3
)

object TaglessTestsEither{
  type P[T]=Either[PuretestError[String],T]

  implicit val fk: Option ~> P =
    λ[Option ~> P](_.toRight(ApplicationError("error")))

  import algebras._
  import handlers._
implicitly[TG1[Either[PuretestError[String],?]]]
  val a1 = TG1[P]
  val a2 = TG2[P]
  val a3 = TG3[P]
}