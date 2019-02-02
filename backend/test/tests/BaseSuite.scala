package tests

import org.scalatest.FunSuite

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class BaseSuite extends FunSuite {
  def await[T](f: Future[T]): T = Await.result(f, 100.seconds)

  def fut[T](t: => T): Future[T] = Future.successful(t)
}
