package com.malliina.pics

extension [L, R](e: Either[L, R])
  def recover[RR >: R](recover: L => RR): RR =
    e.fold(recover, identity)
