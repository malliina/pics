package com.malliina.pics.http4s

import cats.Show
import cats.data.NonEmptyList
import cats.effect.*
import com.malliina.http.{FullUrl, OkHttpResponse}
import com.malliina.http.io.HttpClientIO
import com.malliina.pics.AppMeta
import com.malliina.pics.http4s.PicsImplicits.*
import munit.FunSuite
import okhttp3.RequestBody
import org.http4s.circe.CirceInstances
import org.http4s.headers.{Accept, MediaRangeAndQValue}
import org.http4s.{EntityDecoder, MediaType, Method, Request, Status, Uri}
import tests.ServerSuite

class PicsServerTests extends munit.CatsEffectSuite with ServerSuite with CirceInstances:
  test("can make request") {
    val res = http.get(baseUrl / "ping").map(_.code)
    assertIO(res, Status.Ok.code)
    val result = http.getAs[AppMeta](baseUrl / "ping")
    assertIO(result, AppMeta.default)
  }

  test("root redirects") {
    val client = HttpClientIO(http.client.newBuilder().followRedirects(false).build())
    val req = client.get(baseUrl)
    req.map(res => assertEquals(res.code, Status.SeeOther.code))
  }

  test("version endpoint returns ok for acceptable Accept header") {
    val req = getUri("version", PicsService.version10)
    req.map(res => assertEquals(res.code, Status.Ok.code))

  }

  test("version endpoint returns not acceptable for unacceptable Accept header") {
    val req = getUri("/version", mediaType"application/vnd.pics.v09+json")
    req.map(res => assertEquals(res.status, Status.NotAcceptable.code))

  }

  test("anon can list pics") {
    val req = getUri("/pics")
    req.map(res => assertEquals(res.status, Status.Ok.code))
  }

  test("anon cannot delete") {
    val req = make("/pics/pic123.jpg/delete", Method.POST, PicsService.version10)
    req.map(res => assertEquals(res.status, Status.Unauthorized.code))
  }

  test("anon cannot sync") {
    val req = make("/sync", Method.POST, PicsService.version10)
    req.map(res => assertEquals(res.status, Status.Unauthorized.code))
  }

  private def getUri(
    uri: String,
    mediaType: MediaType = PicsService.version10
  ): IO[OkHttpResponse] =
    http.get(baseUrl / uri, Map("Accept" -> Show[MediaType].show(mediaType)))

  def make(uri: String, method: Method, mediaType: MediaType): IO[OkHttpResponse] =
    http.post(
      baseUrl / uri,
      RequestBody.create(Array.empty[Byte]),
      Map("Accept" -> Show[MediaType].show(mediaType))
    )

  def baseUrl: FullUrl = server().baseHttpUrl
  def http: HttpClientIO = client()
