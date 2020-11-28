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
    val service = app()
    val pingRequest = get(uri"/ping")
    val response = service.run(pingRequest).unsafeRunSync()
    assertEquals(response.status, Status.Ok)
    implicit val dec = jsonOf[IO, AppMeta]
    val result = response.as[AppMeta].unsafeRunSync()
    assertEquals(result, AppMeta.default)
  }

  test("root redirects") {
    val service = app()
    val req = get(uri"/")
    val response = service.run(req).unsafeRunSync()
    assertEquals(response.status, Status.SeeOther)
  }

  test("version endpoint returns ok for acceptable Accept header") {
    val res = versionWith(PicsService.version10)
    assertEquals(res.status, Status.Ok)
  }

  test("version endpoint returns not acceptable for unacceptable Accept header") {
    val res = versionWith(mediaType"application/vnd.pics.v09+json")
    assertEquals(res.status, Status.NotAcceptable)
  }

  private def versionWith(mediaType: MediaType) = {
    val req = get(uri"/version").putHeaders(
      Accept(NonEmptyList.of(MediaRangeAndQValue.withDefaultQValue(mediaType)))
    )
    app().run(req).unsafeRunSync()
  }

  def get(uri: Uri) = Request[IO](Method.GET, uri)
}
