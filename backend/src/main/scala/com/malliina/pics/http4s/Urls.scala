package com.malliina.pics.http4s

import com.malliina.http.FullUrl
import org.http4s.{Request, Uri}
import org.http4s.headers.Host
import org.typelevel.ci.CIStringSyntax

object Urls:
  def toUri(url: FullUrl) = Uri.unsafeFromString(url.url)

  def hostOnly[F[_]](req: Request[F]): FullUrl =
    val proto = if isSecure(req) then "https" else "http"
    val uri = req.uri
    val cloudHost = req.headers.get(ci"X-Forwarded-Host").map(_.head.value)
    val directHost = req.headers
      .get[Host]
      .map(hp => hp.port.fold(hp.host)(port => s"${hp.host}:$port"))
    val hostAndPort =
      cloudHost.orElse(uri.host.map(_.value)).orElse(directHost).getOrElse("localhost")
    FullUrl(proto, hostAndPort, "")

  def topDomainFrom(req: Request[?]): String = topDomain(hostOnly(req).host)

  private def topDomain(in: String): String =
    in.split('.').takeRight(2).mkString(".").takeWhile(c => c != ':' && c != '/')

  def isSecure[F[_]](req: Request[F]): Boolean =
    req.isSecure.getOrElse(false) || req.headers
      .get(ci"X-Forwarded-Proto")
      .exists(_.head.value == "https")
