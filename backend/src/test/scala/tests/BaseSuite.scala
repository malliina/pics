package tests

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class BaseSuite extends munit.FunSuite:
  def await[T](f: Future[T]): T = Await.result(f, 30.seconds)

  def fut[T](t: => T): Future[T] = Future.successful(t)
