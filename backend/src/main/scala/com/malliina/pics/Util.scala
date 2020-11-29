package com.malliina.pics

import cats.effect.IO

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

object Util {
  def timed[T](work: => T): Timed[T] = {
    val start = System.currentTimeMillis()
    val t = work
    val end = System.currentTimeMillis()
    Timed(t, (end - start).millis)
  }

  def runTimed[T](work: => Future[T])(implicit ec: ExecutionContext): Future[Timed[T]] = {
    val start = System.currentTimeMillis()
    work.map { t =>
      Timed(t, (System.currentTimeMillis() - start).millis)
    }
  }

  def timedIO[T](work: IO[T]): IO[Timed[T]] =
    IO(System.currentTimeMillis()).flatMap { start =>
      work.flatMap { t =>
        IO(System.currentTimeMillis()).map { end =>
          Timed(t, (end - start).millis)
        }
      }
    }
}

case class Timed[T](result: T, duration: FiniteDuration)
