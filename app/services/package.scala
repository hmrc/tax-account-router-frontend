import scala.concurrent.{ExecutionContext, Future}

package object services {

  implicit class ListX[A](as: List[A]) {

    def find(predicate: A => Future[Boolean])(implicit ec: ExecutionContext): Future[Option[A]] = as match {
      case Nil => Future.successful(None)
      case a :: tail => predicate(a) flatMap {
        case true => Future.successful(Some(a))
        case false => tail.find(predicate)
      }
    }

    def forall(predicate: A => Future[Boolean])(implicit ec: ExecutionContext): Future[Boolean] = as match {
      case Nil => Future.successful(true)
      case a :: tail => predicate(a) flatMap {
        case true => tail.forall(predicate)
        case false => Future.successful(false)
      }
    }
  }

}
