/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers

import actions.AuthAction
import config.FrontendAppConfig
import connector.EacdConnector
import play.api.libs.json.Json
import play.api.mvc._
import services._
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Individual, Organisation}
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import javax.inject.{Inject, Singleton}
import model.UserProfile
import play.api.Logging

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RouterController @Inject()(val authConnector: AuthConnector,
                                 authAction: AuthAction,
                                 appConfig: FrontendAppConfig,
                                 val messagesControllerComponents: MessagesControllerComponents,
                                 eacd: EacdConnector,
                                 auditService: AuditService)
                                (implicit val ec: ExecutionContext)
  extends FrontendController(messagesControllerComponents) with AuthorisedFunctions with Logging {

  private val saEnrolmentSet: Set[String] = Set("IR-SA", "HMRC-MTD-IT", "HMRC-NI")

  private def route(reason: String, location: String, userProfile: UserProfile)(implicit e: Set[Enrolment], hc: HeaderCarrier, request: Request[_]): Future[Result] = {
    val data = Json.parse(
      s"""{
         |  "reason": "$reason",
         |  "location": "$location",
         |  "enrolments": ${Json.toJson(e)},
         |  "affinityGroup": "${userProfile.affinityGroup.getOrElse("")}",
         |  "confidenceLevel": "${userProfile.confidenceLevel.level}",
         |  "credentials": "${userProfile.credentials.getOrElse("")}",
         |  "credentialRole": "${userProfile.credentialRole.getOrElse("")}"
         |}""".stripMargin
    )

    auditService.audit(AuditModel(reason, data))
    logger.info(s"[RouterController][route] $location $reason")
    Future {
      location match {
        case "PTA" => Redirect(appConfig.pta)
        case "AgentServices" => Redirect(appConfig.agents)
        case "AgentClassic" => Redirect(appConfig.agentsClassic)
        case _ => Redirect(appConfig.bta)
      }
    }
  }

  private def PTA(reason: String, userProfile: UserProfile)(implicit e: Set[Enrolment], hc: HeaderCarrier, request: Request[_]): Future[Result] = route(reason, "PTA", userProfile)

  private def BTA(reason: String, userProfile: UserProfile)(implicit e: Set[Enrolment], hc: HeaderCarrier, request: Request[_]): Future[Result] = route(reason, "BTA", userProfile)

  private def AgentServices(userProfile: UserProfile)(implicit e: Set[Enrolment], hc: HeaderCarrier, request: Request[_]): Future[Result] = route("agent-services", "AgentServices", userProfile)

  private def AgentClassic(userProfile: UserProfile)(implicit e: Set[Enrolment], hc: HeaderCarrier, request: Request[_]): Future[Result] = route("agent-classic", "AgentClassic", userProfile)


  def redirectUser: Action[AnyContent] = {
    Action.async { implicit request =>
      authAction.userProfile { user =>
        implicit val enrolments: Set[Enrolment] = user.enrolments

        def isNotGateway: Boolean = user.credentials.exists(_.providerType == "Verify")

        def isVerified: Boolean = user.confidenceLevel >= L200

        def isAdmin: Boolean = user.credentialRole.contains(User)

        def hasOnlySAenrolments: Boolean = enrolments.nonEmpty && enrolments.map(_.key).forall(e => saEnrolmentSet(e))

        def hasNoGroupEnrolments: Future[Boolean] = eacd.checkGroupEnrolments(user.groupId)

        def isAffinity(a: AffinityGroup): Boolean = user.affinityGroup.contains(a)

        def activeAgent: Boolean = enrolments.map(_.key).contains("HMRC-AS-AGENT")

        def hasNonSAenrolments = enrolments.collect { case e if e.key != "HMRC-AS-AGENT" => e.key }.exists(e => !saEnrolmentSet(e))

        def hasPTEnrolment: Boolean = enrolments.nonEmpty && enrolments.map(_.key).contains("HMRC-PT")

        if (isAffinity(Organisation)) {
          if (hasPTEnrolment) {
            BTA("user-having-OrganisationAffinity-and-PTEnrolment-routed-to-BTA-would-have-gone-to-PTA", user)
          } else if (isNotGateway) {
            BTA("user-having-OrganisationAffinity-and-is-not-a-gatewayUser-routed-to-BTA-would-have-gone-to-PTA", user)
          } else if (isAdmin) {
            if (hasOnlySAenrolments) {
              if (isVerified)
                BTA("user-with-OrganisationAffinity-Admin-sa-enrolments-and-cl250-routed-to-BTA-would-have-gone-to-PTA", user)
              else
                BTA("user-with-OrganisationAffinity-Admin-sa-enrolments-and-not-cl250-routed-to-BTA-would-have-gone-to-BTA", user)
            } else if (hasNonSAenrolments) {
              BTA("user-with-OrganisationAffinity-no-sa-enrolments-routed-to-BTA-would-have-gone-to-BTA", user)
            }
          }
          BTA("user-with-orgAffinity-routed-to-BTA-would-have-gone-to-BTA", user)
        } else if (isAffinity(Agent)) {
          if (activeAgent) {
            AgentServices(user)
          } else {
            AgentClassic(user)
          }
        } else if (hasPTEnrolment) {
          PTA("PT-enrolment-user", user)
        } else if (isNotGateway) PTA("verify-user", user) else if (isAdmin) {
          if (hasOnlySAenrolments) {
            if (isVerified) PTA("user-with-sa-enrolments-and-cl250", user) else BTA("user-with-sa-enrolments-and-not-cl250", user)
          } else if (hasNonSAenrolments) {
            BTA("no-sa-enrolments", user)
          } else {
            hasNoGroupEnrolments flatMap { noEnrolments =>
              if (noEnrolments) {
                  PTA("individual-account", user)
              } else BTA("group-credential", user)
            }
          }
        } else BTA("assistant-credential", user)
      }
    }
  }
}

