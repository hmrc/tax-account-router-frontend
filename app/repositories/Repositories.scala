/*
 * Copyright 2015 HM Revenue & Customs
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

package repositories

import model.Location._
import play.api.libs.json.{JsArray, JsString, Json}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.bson._
import reactivemongo.core.commands.DefaultCommandError
import uk.gov.hmrc.cache.model.Cache
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.mongo.DatabaseUpdate
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

class RoutingCacheRepository(implicit mongo: () => DB) extends CacheMongoRepository(collName = "routing", expireAfterSeconds = 7776000)

object RoutingCacheRepository extends MongoDbConnection {

  def apply(): RoutingCacheRepository = new RoutingCacheRepository

}

case class HourlyLimitId(location: LocationType, hour: Int) {
  def value: String = s"${location.name}-$hour"
}

object CacheFormat {
  val format = ReactiveMongoFormats.mongoEntity(Cache.cacheFormat)
}

class HourlyLimitsCacheRepository(implicit mongo: () => DB) extends CacheMongoRepository(collName = "hourlyLimits", expireAfterSeconds = 7200) {

  implicit val myCacheFormat = CacheFormat.format

  def createOrUpdate(id: HourlyLimitId, hourlyLimit: Int, userId: String)(implicit ec: ExecutionContext): Future[Option[DatabaseUpdate[Cache]]] = {

    val selector = BSONDocument(
      "_id" -> BSONString(id.value),
      "data.users" -> BSONDocument("$not" -> BSONDocument("$in" -> BSONArray(BSONString(userId)))),
      "$where" -> BSONString(s"this.data.users.length < $hourlyLimit")
    )

    val modifier = withCurrentTime { time =>

      val now = time.getMillis

      List(
        BSONDocument("$addToSet" -> BSONDocument("data.users" -> BSONString(userId))),
        BSONDocument(
          "$set" -> BSONDocument("modifiedDetails.lastUpdated" -> BSONDateTime(now)),
          "$setOnInsert" -> BSONDocument("modifiedDetails.createdAt" -> BSONDateTime(now))
        )
      ).reduce(_ ++ _)
    }

    atomicSaveOrUpdate(selector, modifier, upsert = true, AtomicId).recover {
      case e: DefaultCommandError =>
        val errorCodeOption: Option[Int] = for {
          document <- e.originalDocument
          errorCode <- document.get("code")
        } yield errorCode.asInstanceOf[BSONInteger].value
        errorCodeOption.flatMap {
          case errorCode if errorCode == 11000 => None
        }
    }
  }

  def exists(id: HourlyLimitId, userId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val selector = Json.obj(
      "_id" -> JsString(id.value),
      "data.users" -> Json.obj("$in" -> JsArray(Seq(JsString(userId))))
    )
    collection.count(Some(selector)).map(_ > 0)
  }
}

object HourlyLimitsCacheRepository extends MongoDbConnection {

  def apply(): HourlyLimitsCacheRepository = new HourlyLimitsCacheRepository

}
