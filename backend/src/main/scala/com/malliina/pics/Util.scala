package com.malliina.pics

import cats.effect.IO
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}

import java.text.Normalizer
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

object Util:
  def timed[T](work: => T): Timed[T] =
    val start = System.currentTimeMillis()
    val t = work
    val end = System.currentTimeMillis()
    Timed(t, (end - start).millis)

  def runTimed[T](work: => Future[T])(implicit ec: ExecutionContext): Future[Timed[T]] =
    val start = System.currentTimeMillis()
    work.map { t =>
      Timed(t, (System.currentTimeMillis() - start).millis)
    }

  def timedIO[T](work: IO[T]): IO[Timed[T]] =
    IO(System.currentTimeMillis()).flatMap { start =>
      work.flatMap { t =>
        IO(System.currentTimeMillis()).map { end =>
          Timed(t, (end - start).millis)
        }
      }
    }

  private val generator = new RandomStringGenerator.Builder()
    .withinRange('a', 'z')
    .filteredBy(CharacterPredicates.LETTERS)
    .build()

  def randomString(length: Int) = generator.generate(length).toLowerCase

  def normalize(input: String): String =
    Normalizer
      .normalize(input, Normalizer.Form.NFD)
      .replaceAll("[^\\p{ASCII}]", "")
      .toLowerCase
      .replaceAll("[^-a-zA-Z0-9]", "-")
      .trim

case class Timed[T](result: T, duration: FiniteDuration)
