package com.malliina.pics.js

import com.malliina.http.FullUrl
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Json}
import org.scalajs.dom
import org.scalajs.dom.{AbortController, Headers, HttpMethod, RequestCredentials, RequestInit, Response}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Http:
  def post[Req: Encoder, Res: Decoder](url: FullUrl, body: Req): Future[Res] =
    make(url, HttpMethod.POST, Option(body.asJson)).flatMap: res =>
      val status = res.status
      if status >= 200 && status <= 300 then
        res
          .text()
          .toFuture
          .flatMap: str =>
            io.circe.parser
              .decode(str)
              .fold(
                err => Future.failed(JsonException(err, res)),
                ok => Future.successful(ok)
              )
      else Future.failed(StatusException(url.url, res))

  def make(
    url: FullUrl,
    method: HttpMethod = HttpMethod.GET,
    data: Option[Json] = None,
    headers: Map[String, String] = Map.empty,
    credentials: RequestCredentials = RequestCredentials.omit
  ): Future[Response] =
    val aborter = AbortController()
    val req = new RequestInit {}
    req.method = method
    data.foreach: json =>
      req.body = json.noSpaces
    req.credentials = credentials
    val hs = new Headers()
    hs.append("Csrf-Token", "nocheck")
    headers.foreach((name, value) => hs.append(name, value))
    req.headers = hs
    req.signal = aborter.signal
    dom.fetch(url.url, req).toFuture

class JsonException(val error: io.circe.Error, val res: dom.Response) extends Exception

class StatusException(val uri: String, val res: dom.Response)
  extends Exception(s"Invalid response code '${res.status}' from '$uri'.")
