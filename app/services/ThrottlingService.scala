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

package services

import cats.data.WriterT
import config.{AppConfig, FrontendAppConfig}
import engine.{AuditInfo, EngineResult, ThrottlingInfo}
import javax.inject.{Inject, Singleton}
import model.Locations.PersonalTaxAccount
import model._
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

case class ThrottlingConfig(percentageToBeThrottled: Int, fallback: Option[String])

trait LocationConfigurationFactory {

  val configuration: AppConfig

  def configurationForLocation(location: Location, request: Request[AnyContent]): ThrottlingConfig = {

    def getLocationSuffix(location: Location, request: Request[AnyContent]): String = {
      location match {
        case PersonalTaxAccount =>
          if (request.session.data.contains("token")) {
            "-gg"
          } else {
            "-verify"
          }
        case _ => ""
      }
    }

    val suffix = getLocationSuffix(location, request)
    configuration.getThrottlingConfig(s"${location.name}$suffix")
  }
}


@Singleton
class ThrottlingService @Inject()(appConfig: FrontendAppConfig)(implicit val ec: ExecutionContext){

  lazy val locationConfigurationFactory: LocationConfigurationFactory = new LocationConfigurationFactory {
    override val configuration: AppConfig = appConfig
  }

  lazy val throttlingEnabled: Boolean = appConfig.throttlingEnabled

  def throttle(currentResult: EngineResult, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): EngineResult = {

    def findFallbackFor(location: Location, throttlingConfig: ThrottlingConfig): Location = {
      val fallback = for {
        fallbackName <- throttlingConfig.fallback
        fallback <- Locations.find(fallbackName)
      } yield {
        fallback
      }

      fallback.getOrElse(location)
    }

    def doThrottle(auditInfo: AuditInfo, location: Location, userId: String, throttlingConfig: ThrottlingConfig): (AuditInfo, Location) = {
      val percentageToBeThrottled = throttlingConfig.percentageToBeThrottled

      if (Throttler.shouldThrottle(userId, percentageToBeThrottled)) {
        val throttleDestination = findFallbackFor(location, throttlingConfig)
        val throttlingInfo = ThrottlingInfo(percentage = Some(percentageToBeThrottled), location != throttleDestination, location, throttlingEnabled)
        (auditInfo.copy(throttlingInfo = Some(throttlingInfo)), throttleDestination)
      } else {
        val throttlingInfo = ThrottlingInfo(percentage = Some(percentageToBeThrottled), throttled = false, location, throttlingEnabled = throttlingEnabled)
        (auditInfo.copy(throttlingInfo = Some(throttlingInfo)), location)
      }
    }

    import cats.instances.all._

    currentResult flatMap { location =>
      val result: Future[(AuditInfo, Location)] = for {
        userIdentifier <- ruleContext.internalUserIdentifier
        auditInfo <- currentResult.written
      } yield userIdentifier match {
        case Some(userId) if throttlingEnabled =>
          val locationConfiguration = locationConfigurationFactory.configurationForLocation(location, request)
          doThrottle(auditInfo, location, userId, locationConfiguration)
        case _ =>
          val throttlingInfo = ThrottlingInfo(percentage = None, throttled = false, location, throttlingEnabled = false)
          (auditInfo.copy(throttlingInfo = Some(throttlingInfo)), location)
      }

      WriterT(result)
    }
  }

}
