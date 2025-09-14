package com.malliina.pics.http4s

import cats.Show
import cats.effect.*
import com.malliina.http.{CSRFConf, FullUrl, HttpClient, HttpHeaders, HttpResponse}
import com.malliina.pics.AppMeta
import org.http4s.circe.CirceInstances
import org.http4s.implicits.mediaType
import org.http4s.{MediaType, Status}

import java.net.http.HttpClient as JHttpClient
import tests.ServerSuite

import java.net.http.HttpClient.Redirect

class PicsServerTests extends munit.CatsEffectSuite with ServerSuite with CirceInstances:
  test("can make request"):
    val res = http.get(baseUrl / "ping").map(_.code)
    assertIO(res, Status.Ok.code)
    val result = http.getAs[AppMeta](baseUrl / "ping")
    assertIO(result, AppMeta.default)

  val noRedir = HttpClient
    .resource[IO](JHttpClient.newBuilder().followRedirects(Redirect.NEVER), Map.empty)
  val noRedirHttp = ResourceFunFixture(noRedir)
  noRedirHttp.test("root redirects"): client =>
    client
      .get(baseUrl)
      .map: res =>
        assertEquals(res.code, Status.SeeOther.code)

  test("version endpoint returns ok for acceptable Accept header"):
    val req = getUri("version", PicsService.version10)
    req.map(res => assertEquals(res.code, Status.Ok.code))

  test("version endpoint returns not acceptable for unacceptable Accept header"):
    val req = getUri("/version", mediaType"application/vnd.pics.v09+json")
    req.map(res => assertEquals(res.status, Status.NotAcceptable.code))

  test("anon can list pics"):
    val req = getUri("/pics")
    req.map(res => assertEquals(res.status, Status.Ok.code))

  test("anon cannot delete"):
    val req = make("/pics/pic123.jpg/delete", PicsService.version10)
    req.map(res => assertEquals(res.status, Status.Unauthorized.code))

  test("anon cannot sync"):
    val req = make("/sync", PicsService.version10)
    req.map(res => assertEquals(res.status, Status.Unauthorized.code))

  private def getUri(
    uri: String,
    mediaType: MediaType = PicsService.version10
  ): IO[HttpResponse] =
    http.get(baseUrl / uri, Map("Accept" -> Show[MediaType].show(mediaType)))

  def make(uri: String, mediaType: MediaType): IO[HttpResponse] =
    val csrf = CSRFConf.default
    http.postString(
      baseUrl / uri,
      "",
      HttpHeaders.application.octetStream,
      Map("Accept" -> Show[MediaType].show(mediaType), csrf.headerName.toString -> csrf.noCheck)
    )

  def baseUrl: FullUrl = server().baseHttpUrl
  def http: HttpClient[IO] = client()
