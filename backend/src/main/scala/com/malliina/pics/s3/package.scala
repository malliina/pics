package com.malliina.pics

import java.util.concurrent.CompletableFuture

import scala.concurrent.{Future, Promise}

package object s3 {
  implicit class CFOps[T](cf: CompletableFuture[T]) {
    def future: Future[T] = asFuture(cf)
  }

  private def asFuture[T](cf: CompletableFuture[T]): Future[T] = {
    val p = Promise[T]()
    cf.whenComplete((r, t) => Option(t).fold(p.success(r))(t => p.failure(t)))
    p.future
  }
}
