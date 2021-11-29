package com.malliina.pics.http4s

import cats.Show
import cats.data.NonEmptyList
import cats.effect.*
import cats.effect.unsafe.implicits.global
import com.malliina.http.FullUrl
import com.malliina.http.io.HttpClientIO
import com.malliina.pics.AppMeta
import com.malliina.pics.http4s.PicsImplicits.*
import munit.FunSuite
import okhttp3.RequestBody
import org.http4s.circe.CirceInstances
import org.http4s.headers.{Accept, MediaRangeAndQValue}
import org.http4s.{EntityDecoder, MediaType, Method, Request, Status, Uri}
import tests.ServerSuite

class PicsServerTests extends FunSuite with ServerSuite with CirceInstances:
  test("can make request") {
    val res = http.get(baseUrl / "ping").unsafeRunSync()
    assertEquals(res.code, Status.Ok.code)
    val result = http.getAs[AppMeta](baseUrl / "ping").unsafeRunSync()
    assertEquals(result, AppMeta.default)
  }

  test("root redirects") {
    val client = HttpClientIO(http.client.newBuilder().followRedirects(false).build())
    val res = client.get(baseUrl).unsafeRunSync()
    assertEquals(res.code, Status.SeeOther.code)
  }

  test("version endpoint returns ok for acceptable Accept header") {
    val res = getUri("version", PicsService.version10)
    assertEquals(res.code, Status.Ok.code)
  }

  test("version endpoint returns not acceptable for unacceptable Accept header") {
    val res = getUri("/version", mediaType"application/vnd.pics.v09+json")
    assertEquals(res.status, Status.NotAcceptable.code)
  }

  test("anon can list pics") {
    val res = getUri("/pics")
    assertEquals(res.status, Status.Ok.code)
  }

  test("anon cannot delete") {
    val res = make("/pics/pic123.jpg/delete", Method.POST, PicsService.version10)
    assertEquals(res.status, Status.Unauthorized.code)
  }

  test("anon cannot sync") {
    val res = make("/sync", Method.POST, PicsService.version10)
    assertEquals(res.status, Status.Unauthorized.code)
  }

  private def getUri(uri: String, mediaType: MediaType = PicsService.version10) =
    val req = http.get(baseUrl / uri, Map("Accept" -> Show[MediaType].show(mediaType)))
    req.unsafeRunSync()

  def make(uri: String, method: Method, mediaType: MediaType) =
    val req = http.post(
      baseUrl / uri,
      RequestBody.create(Array.empty[Byte]),
      Map("Accept" -> Show[MediaType].show(mediaType))
    )
    req.unsafeRunSync()

  def baseUrl: FullUrl = server().baseHttpUrl
  def http: HttpClientIO = client()
