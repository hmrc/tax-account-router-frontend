package model

import connector._
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext

import scala.concurrent.Future

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

case class RuleContext(authContext: AuthContext)(implicit hc: HeaderCarrier) {
  val governmentGatewayConnector: GovernmentGatewayConnector = GovernmentGatewayConnector
  val selfAssessmentConnector: SelfAssessmentConnector = SelfAssessmentConnector

  lazy val activeEnrolments: Future[Set[String]] = {
    val futureProfile: Future[ProfileResponse] = governmentGatewayConnector.profile
    futureProfile.map { profile =>
      profile.enrolments.filter(_.state == EnrolmentState.ACTIVATED).map(_.key).toSet[String]
    }
  }

  lazy val lastSaReturn: Future[SaReturn] = authContext.principal.accounts.sa
    .fold(Future(SaReturn.empty))(saAccount => selfAssessmentConnector.lastReturn(saAccount.utr.value))
}