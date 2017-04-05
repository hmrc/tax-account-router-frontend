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

import cats.data.WriterT
import cats.{FlatMap, Functor}
import model.Location

import scala.concurrent.{ExecutionContext, Future}

package object engine {

  implicit def futureFunctor(implicit ec: ExecutionContext): Functor[Future] = new Functor[Future] {
    override def map[A, B](fa: Future[A])(f: (A) => B): Future[B] = fa.map(f)
  }

  implicit def futureFlatMap(implicit ec: ExecutionContext): FlatMap[Future] = new FlatMap[Future] {
    override def flatMap[A, B](fa: Future[A])(f: (A) => Future[B]): Future[B] = fa flatMap f

    override def tailRecM[A, B](a: A)(f: (A) => Future[Either[A, B]]): Future[B] = f(a).flatMap {
      case Right(b) => Future.successful(b)
      case Left(aa) => tailRecM(aa)(f)
    }

    override def map[A, B](fa: Future[A])(f: (A) => B): Future[B] = fa map f
  }

  type ConditionResult = WriterT[Future, AuditInfo, Boolean]
  type RuleResult = WriterT[Future, AuditInfo, Option[Location]]
  type EngineResult = WriterT[Future, AuditInfo, Location]
  val emptyRuleResult: RuleResult = WriterT(Future.successful((AuditInfo.Empty, None)))

}
