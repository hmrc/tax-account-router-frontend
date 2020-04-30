/*
 * Copyright 2020 HM Revenue & Customs
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

package support

import java.nio.charset.Charset

import akka.stream.Materializer
import akka.util.{ByteString, Timeout}
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{ResponseHeader, Result}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, _}
import play.api.http.HeaderNames._
import play.api.http.Status._

abstract class UnitSpec extends WordSpec with Matchers {

  implicit def toFuture[A](a:A) : Future[A] = Future.successful(a)

  implicit def unFuture[A](a:Future[A]) : A = Await.result(a,10 seconds)

  implicit val timeout : Duration = 5 minutes

  def await[A](future: Future[A])(implicit timeout: Duration): A = Await.result(future, timeout)

  def redirectLocation(of: Result)(implicit timeout: Timeout): Option[String] = of.header match {
    case ResponseHeader(FOUND, headers, _) => headers.get(LOCATION)
    case ResponseHeader(SEE_OTHER, headers, _) => headers.get(LOCATION)
    case ResponseHeader(TEMPORARY_REDIRECT, headers, _) => headers.get(LOCATION)
    case ResponseHeader(MOVED_PERMANENTLY, headers, _) => headers.get(LOCATION)
    case ResponseHeader(_, _, _) => None
  }


  def status(of: Result): Int = of.header.status

  def status(of: Future[Result])(implicit timeout: Duration): Int = status(Await.result(of, timeout))

  def bodyOf(result: Result)(implicit mat: Materializer): String = {
    val bodyBytes: ByteString = await(result.body.consumeData)
    bodyBytes.decodeString(Charset.defaultCharset().name)
  }

  def bodyOf(resultF: Future[Result])(implicit mat: Materializer): Future[String] = resultF.map(bodyOf)

  def jsonBodyOf(result: Result)(implicit mat: Materializer): JsValue = Json.parse(bodyOf(result))

  def jsonBodyOf(resultF: Future[Result])(implicit mat: Materializer): Future[JsValue] = resultF.map(jsonBodyOf)

}

