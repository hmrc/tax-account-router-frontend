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
import uk.gov.hmrc.auth.core.AffinityGroup.{Individual, Organisation}
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RouterController @Inject()(val authConnector: AuthConnector,
                                 authAction: AuthAction,
                                 appConfig: FrontendAppConfig,
                                 val messagesControllerComponents: MessagesControllerComponents,
                                 eacd: EacdConnector,
                                 auditService: AuditService)
                                (implicit val ec: ExecutionContext)
  extends FrontendController(messagesControllerComponents) with AuthorisedFunctions {

  private val saEnrolmentSet: Set[String] = Set("IR-SA", "HMRC-MTD-IT", "HMRC-NI")

  private def route(reason: String, location: String)(implicit e: Set[Enrolment], hc: HeaderCarrier, request: Request[_]): Future[Result] = {
    val data = Json.parse(
      s"""{
         |  "reason": "$reason",
         |  "location": "$location",
         |  "enrolments": ${Json.toJson(e)}
         |}""".stripMargin
    )
    auditService.audit(AuditModel(reason, data))

    Future {
      location match {
        case "PTA" => Redirect(appConfig.pta)
        case "AgentServices" => Redirect(appConfig.agents)
        case "AgentClassic" => Redirect(appConfig.agentsClassic)
        case _ => Redirect(appConfig.bta)
      }
    }
  }

  private def PTA(reason: String)(implicit e: Set[Enrolment], hc: HeaderCarrier, request: Request[_]): Future[Result] = route(reason, "PTA")
  private def BTA(reason: String)(implicit e: Set[Enrolment], hc: HeaderCarrier, request: Request[_]): Future[Result] = route(reason, "BTA")
  private def AgentServices()(implicit e: Set[Enrolment], hc: HeaderCarrier, request: Request[_]): Future[Result] = route("agent-services", "AgentServices")
  private def AgentClassic()(implicit e: Set[Enrolment], hc: HeaderCarrier, request: Request[_]): Future[Result] = route("agent-classic", "AgentClassic")


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

        def activeAgent: Boolean = enrolments.size == 1 && enrolments.map(_.key).contains("HMRC-AS-AGENT")

        def hasNonSAenrolments = enrolments.collect { case e if e.key != "HMRC-AS-AGENT" => e.key }.exists(e => !saEnrolmentSet(e))

        if (isNotGateway) PTA("verify-user") else if (isAdmin) {
          if (hasOnlySAenrolments) {
            if (isVerified) PTA("user-with-sa-enrolments-and-cl250") else BTA("user-with-sa-enrolments-and-not-cl250")
          } else if (hasNonSAenrolments) {
            BTA("no-sa-enrolments")
          } else {
            hasNoGroupEnrolments flatMap { noEnrolments =>
              if (noEnrolments) {
                if (isAffinity(Organisation)) {
                  BTA("organisation-account")
                } else if (isAffinity(Individual)) {
                  PTA("individual-account")
                } else if (activeAgent) {
                  AgentServices()
                } else {
                  AgentClassic()
                }
              } else BTA("group-credential")
            }
          }
        } else BTA("assistant-credential")
      }
    }
  }

}
