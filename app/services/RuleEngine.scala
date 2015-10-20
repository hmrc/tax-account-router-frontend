package services

import model.AuditEventType._
import model.Location._
import model._
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.auth.AuthContext

import scala.concurrent.Future
import scala.util.Success

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

trait RuleEngine {

  val rules: List[Rule]

  def findLocation(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier) = {

    rules.foldLeft(Future[Option[LocationType]](None)) {
      (location, rule) => location.flatMap(candidateLocation => if (candidateLocation.isDefined) location else rule.apply(authContext, ruleContext, auditContext))
    }
  }


}


trait Condition {

  self =>

  val auditType: Option[AuditEventType]

  def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean]

  def evaluate(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
    this.isTrue(authContext, ruleContext).andThen { case Success(result) if auditType.isDefined => auditContext.setValue(auditType.get, result) }
  }

  def and(c: Condition): Condition = new Operator {

    override def evaluate(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
      val condition1FutureResult: Future[Boolean] = self.evaluate(authContext, ruleContext, auditContext)
      condition1FutureResult.flatMap(c1r => if (c1r) c.evaluate(authContext, ruleContext, auditContext).map(c2r => c1r && c2r) else condition1FutureResult)
    }
  }

  def or(c: Condition): Condition = new Operator {

    override def evaluate(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = {
      val condition1FutureResult: Future[Boolean] = self.evaluate(authContext, ruleContext, auditContext)
      condition1FutureResult.flatMap(c1r => if (c1r) condition1FutureResult else c.evaluate(authContext, ruleContext, auditContext))
    }

  }


}

trait Operator extends Condition {
  override val auditType: Option[AuditEventType] = None
  override def isTrue(authContext: AuthContext, ruleContext: RuleContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] = throw new RuntimeException("This should never be called")
}

object Condition {
  def not(condition: Condition): Condition = new Operator {
    override def evaluate(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
      condition.evaluate(authContext, ruleContext, auditContext).map(!_)
  }
}

case class When(condition: Condition) {


  def thenGoTo(l: LocationType): Rule = new Rule {
    override def apply(authContext: AuthContext, ruleContext: RuleContext, auditContext: TAuditContext)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Option[LocationType]] =
      condition.evaluate(authContext, ruleContext, auditContext) map {
        case true => Some(l)
        case false => None
      }
  }
}