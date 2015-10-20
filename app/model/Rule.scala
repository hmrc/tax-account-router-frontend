package model

import model.Location.LocationType
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext

import scala.concurrent.Future

trait Rule {

  def apply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)
           (implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Option[LocationType]]

}
