package com.malliina.pics

import java.util.concurrent.CompletableFuture

import cats.effect.Async

package object s3:
  implicit class CFOps[T](cf: CompletableFuture[T]):
    def io[F[_]: Async]: F[T] = asIO(cf)

  private def asIO[F[_]: Async, T](cf: CompletableFuture[T]): F[T] = Async[F].async_ : cb =>
    cf.whenComplete((r, t) => Option(t).fold(cb(Right(r)))(t => cb(Left(t))))
