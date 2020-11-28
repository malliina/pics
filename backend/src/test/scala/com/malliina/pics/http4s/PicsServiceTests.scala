package com.malliina.pics.http4s

import cats.data.NonEmptyList
import cats.effect._
import com.malliina.pics.AppMeta
import com.malliina.pics.http4s.PicsImplicits._
import munit.FunSuite
import org.http4s._
import org.http4s.headers.{Accept, MediaRangeAndQValue}
import tests.Http4sSuite

class PicsServiceTests extends FunSuite with Http4sSuite {
  test("can make request") {
    val res = getUri(uri"/ping")
    assertEquals(res.status, Status.Ok)
    implicit val dec = jsonOf[IO, AppMeta]
    val result = res.as[AppMeta].unsafeRunSync()
    assertEquals(result, AppMeta.default)
  }

  test("root redirects") {
    val res = getUri(uri"/")
    assertEquals(res.status, Status.SeeOther)
  }

  test("version endpoint returns ok for acceptable Accept header") {
    val res = getUri(uri"/version", PicsService.version10)
    assertEquals(res.status, Status.Ok)
  }

  test("version endpoint returns not acceptable for unacceptable Accept header") {
    val res = getUri(uri"/version", mediaType"application/vnd.pics.v09+json")
    assertEquals(res.status, Status.NotAcceptable)
  }

  test("anon can list pics") {
    val res = getUri(uri"/pics")
    assertEquals(res.status, Status.Ok)
  }

  test("anon cannot delete") {
    val req = Request[IO](Method.POST, uri"/pics/pic123.jpg/delete")
    val res = app().run(req).unsafeRunSync()
    assertEquals(res.status, Status.Unauthorized)
  }

  test("anon cannot sync") {
    val req = Request[IO](Method.POST, uri"/sync")
    val res = app().run(req).unsafeRunSync()
    assertEquals(res.status, Status.Unauthorized)
  }

  private def getUri(uri: Uri, mediaType: MediaType = PicsService.version10) = {
    val req = get(uri, mediaType)
    app().run(req).unsafeRunSync()
  }

  def get(uri: Uri, mediaType: MediaType) = Request[IO](Method.GET, uri).putHeaders(
    Accept(NonEmptyList.of(MediaRangeAndQValue.withDefaultQValue(mediaType)))
  )
}
