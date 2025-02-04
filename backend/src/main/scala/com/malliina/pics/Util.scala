package com.malliina.pics

import cats.effect.Sync
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}

import java.text.Normalizer
import scala.concurrent.duration.{DurationLong, FiniteDuration}

object Util:
  def timed[T](work: => T): Timed[T] =
    val start = System.currentTimeMillis()
    val t = work
    val end = System.currentTimeMillis()
    Timed(t, (end - start).millis)

  def timedIO[F[_]: Sync, T](work: F[T]): F[Timed[T]] =
    Sync[F]
      .delay(System.currentTimeMillis())
      .flatMap: start =>
        work.flatMap: t =>
          Sync[F]
            .delay(System.currentTimeMillis())
            .map: end =>
              Timed(t, (end - start).millis)

  private val generator = new RandomStringGenerator.Builder()
    .withinRange('a', 'z')
    .filteredBy(CharacterPredicates.LETTERS)
    .get()

  def randomString(length: Int) = generator.generate(length).toLowerCase

  def normalize(input: String): String =
    Normalizer
      .normalize(input, Normalizer.Form.NFD)
      .replaceAll("[^\\p{ASCII}]", "")
      .toLowerCase
      .replaceAll("[^-a-zA-Z0-9]", "-")
      .trim

case class Timed[T](result: T, duration: FiniteDuration)
