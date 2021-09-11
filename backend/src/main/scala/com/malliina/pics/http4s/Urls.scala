package com.malliina.pics.http4s

import com.malliina.http.FullUrl
import org.http4s.Request
import org.http4s.headers.Host
import org.typelevel.ci.CIStringSyntax

object Urls:
  def hostOnly[F[_]](req: Request[F]): FullUrl =
    val proto = if isSecure(req) then "https" else "http"
    val uri = req.uri
    val hostAndPort =
      req.headers
        .get[Host]
        .map(h => h.port.map(p => s"${h.host}:$p").getOrElse(h.host))
        .getOrElse("localhost")
    FullUrl(proto, uri.host.map(_.value).getOrElse(hostAndPort), "")

  def isSecure[F[_]](req: Request[F]): Boolean =
    req.isSecure.getOrElse(false) || req.headers
      .get(ci"X-Forwarded-Proto")
      .exists(_.head.value == "https")
