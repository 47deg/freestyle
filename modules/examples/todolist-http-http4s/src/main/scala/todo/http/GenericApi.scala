/*
 * Copyright 2017-2018 47 Degrees, LLC. <http://www.47deg.com>
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

package examples.todolist
package http

import cats.Monad
import examples.todolist.model.Pong
import io.circe.Json
import org.http4s.HttpService
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

class GenericApi[F[_]: Monad] extends Http4sDsl[F] {
  val service: HttpService[F] =
    HttpService[F] {

      case GET -> Root / "ping" =>
        Ok(Json.fromLong(Pong.current.time))

      case GET -> Root / "hello" =>
        Ok("Hello World")

    }
}

object GenericApi {
  implicit def instance[F[_]: Monad](): GenericApi[F] =
    new GenericApi[F]
}
