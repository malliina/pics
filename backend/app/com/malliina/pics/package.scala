package com.malliina

package object pics {
  implicit class EitherOps[L, R](val e: Either[L, R]) extends AnyVal {
    def recover[RR >: R](recover: L => RR): RR =
      e.fold(recover, identity)
  }
}
