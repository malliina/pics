package com.malliina.pics.http4s

import cats.effect._
import com.malliina.pics.AppMeta
import com.malliina.pics.http4s.PicsImplicits._
import munit.FunSuite
import org.http4s._
import tests.Http4sSuite

class PicsServiceTests extends FunSuite with Http4sSuite {
  test("can make request") {
    val service = app()
    val pingRequest = Request[IO](Method.GET, uri"/ping")
    val response = service.run(pingRequest).unsafeRunSync()
    assertEquals(response.status, Status.Ok)
    implicit val dec = jsonOf[IO, AppMeta]
    val result = response.as[AppMeta].unsafeRunSync()
    assertEquals(result, AppMeta.default)
  }
}
