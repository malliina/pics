package com.malliina.pics

import org.http4s.Request

package object http4s:
  implicit class RequestOps[F[_]](val req: Request[F]) extends AnyVal:
    def isSecured: Boolean = Urls.isSecure(req)
