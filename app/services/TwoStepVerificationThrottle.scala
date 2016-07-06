package services

import scala.concurrent.Future

trait TwoStepVerificationThrottle {
  def registrationMandatory(discriminator: String): Future[Boolean] = ???
}

object TwoStepVerificationThrottle extends TwoStepVerificationThrottle