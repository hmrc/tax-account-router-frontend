/*
 * Copyright 2017 HM Revenue & Customs
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

import scala.concurrent.{ExecutionContext, Future}

package object services {

  implicit class ListX[A](as: List[A]) {

    def findOne(predicate: A => Future[Boolean])(implicit ec: ExecutionContext): Future[Option[A]] = as match {
      case Nil => Future.successful(None)
      case head :: tail => predicate(head) flatMap {
        case true => Future.successful(Some(head))
        case false => tail.findOne(predicate)
      }
    }

    def forAll(predicate: A => Future[Boolean])(implicit ec: ExecutionContext): Future[Boolean] = as match {
      case Nil => Future.successful(true)
      case head :: tail => predicate(head) flatMap {
        case true => tail.forAll(predicate)
        case false => Future.successful(false)
      }
    }
  }

}
