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

package services

import connector.InternalUserIdentifier
import model.Location
import org.joda.time.DateTime
import play.api.Configuration
import repositories.{HourlyLimitId, HourlyLimitsCacheRepository}

import scala.concurrent.{ExecutionContext, Future}

trait HourlyLimitService {

  def hourlyLimitsCacheRepository: HourlyLimitsCacheRepository

  def applyHourlyLimit(location: Location, fallbackLocation: Location, userIdentifier: InternalUserIdentifier, configurationForLocation: Configuration)(implicit ec: ExecutionContext): Future[Location] = {
    ???
  }

  def applyHourlyLimit(location: Location, fallbackLocation: Location, userIdentifier: InternalUserIdentifier, hourlyLimit: Option[Int], hourOfDay: Int)(implicit ec: ExecutionContext): Future[Location] = {

    hourlyLimit match {
      case Some(limit) =>
        val id = HourlyLimitId(location, hourOfDay)
        val updateResult = hourlyLimitsCacheRepository.createOrUpdate(id, limit, userIdentifier)

        updateResult.flatMap {
          resultOption =>
            resultOption.map { _ =>
              Future(location)
            }.getOrElse {
              val hourlyLimitAlreadyExists = hourlyLimitsCacheRepository.exists(id, userIdentifier)
              hourlyLimitAlreadyExists.map {
                case true => location
                case false => fallbackLocation
              }
            }
        }
      case None => Future(location)
    }
  }
}

object HourlyLimitService extends HourlyLimitService {
  override def hourlyLimitsCacheRepository: HourlyLimitsCacheRepository = HourlyLimitsCacheRepository()
}
