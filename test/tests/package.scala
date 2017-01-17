import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

package object tests {
  def await[T](f: Future[T]): T = Await.result(f, 10.seconds)

  def fut[T](t: => T): Future[T] = Future.successful(t)
}
