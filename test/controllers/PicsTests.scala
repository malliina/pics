package controllers

import com.malliina.pics.PicOwner
import play.api.http.Writeable
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import tests.{TestAppSuite, TestAuthenticator, await}

class PicsTests extends TestAppSuite {
  test("root redirects") {
    val result = makeGet("/")
    assert(result.header.status === 303)
  }

  test("no auth returns unauthorized") {
    // useless test, because prod defaults to anon, unlike the test impl
    val result = makeGet("/pics")
    assert(result.header.status === 401)
  }

  test("anon can list") {
    val result = makeGet(s"/pics$anonQuery")
    assert(result.header.status === 200)
  }

  test("anon cannot delete") {
    val result = makeEmptyPost(s"/pics/pic123.jpg/delete$anonQuery")
    assert(result.header.status === 401)
  }

  test("user can list") {
    val result = makeGet(s"/pics$userQuery")
    assert(result.header.status === 200)
  }

  test("user can delete") {
    val result = makeEmptyPost(s"/pics/nonexistent.jpg/delete$userQuery")
    assert(result.header.status === 404)
  }

  test("user cannot sync") {
    val result = makeEmptyPost(s"/sync$userQuery")
    assert(result.header.status === 401)
  }

  test("non-image upload fails with bad request") {
    val result = makePost(s"/pics$anonQuery", "boom")
    assert(result.header.status === 400)
  }

  def anonQuery = testAuthQuery(PicOwner.anon.name)

  def userQuery = testAuthQuery(TestAuthenticator.TestUser)

  def testAuthQuery(user: String) = s"?${TestAuthenticator.TestQuery}=$user"

  def makePost[B: Writeable](uri: String, body: B) = makeRequest(FakeRequest(POST, uri).withBody(body))

  def makeEmptyPost(uri: String) = makeRequest(FakeRequest(POST, uri))

  def makeGet(uri: String) = makeRequest(FakeRequest(GET, uri))

  def makeRequest[T: Writeable](req: Request[T]) =
    await(route(app, req).get)
}
