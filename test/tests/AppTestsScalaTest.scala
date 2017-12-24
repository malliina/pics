package tests

import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.pics.PicSources
import com.malliina.pics.app.AppComponents
import play.api.http.Writeable
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._

abstract class TestAppSuite extends AppSuite(ctx => new AppComponents(
  ctx,
  GoogleOAuthCredentials("id", "secret", "scope"),
  _ => PicSources.clones(TestPics)
))

class AppTests extends TestAppSuite {
  test("can make request") {
    val result = makeRequest(FakeRequest(GET, "/"))
    assert(result.header.status === 303)
  }

  test("can upload file") {
    val result = makeRequest(FakeRequest(POST, "/pics").withBody("boom"))
    assert(result.header.status === 303)
  }

  def makeRequest[T: Writeable](req: Request[T]) =
    await(route(app, req).get)
}
