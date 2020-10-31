package tests

import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.play.http.{PlayUtils, Proxies}
import play.api.ApplicationLoader.Context
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc._
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Configuration, NoHttpFiltersComponents, Play}

class TestComponents(ctx: Context)
  extends BuiltInComponentsFromContext(ctx)
  with NoHttpFiltersComponents {

  import play.api.routing.sird._

  override lazy val configuration: Configuration = Configuration(
    "play.http.forwarded.version" -> "x-forwarded",
    "play.http.forwarded.trustedProxies" -> List("0.0.0.0/0", "::/0")
  ).withFallback(ctx.initialConfiguration)

  override def router: Router = Router.from {
    case GET(p"/")    => securityCheck(_.secure)
    case GET(p"/mod") => securityCheck(Proxies.isSecure)
  }

  def securityCheck(isSecure: RequestHeader => Boolean) = Action { req =>
    Results.Ok(Json.obj("secure" -> isSecure(req), "headers" -> PlayUtils.headersString(req)))
  }
}

class SiloTest extends munit.FunSuite with PlayAppSuite {
//  import play.api.test.Helpers._

  test("play says secure is false despite https in trusted proxy") {
    assert(!makeReq("/"))
  }

  test("Proxies.isSecure says https") {
    assert(makeReq("/mod"))
  }

  def makeReq(url: String): Boolean = {
    val reqHeaders = Headers(
      HeaderNames.X_FORWARDED_PROTO -> "https",
      HeaderNames.X_FORWARDED_FOR -> "10.0.0.1"
    )
//    val res = makeRequest(FakeRequest(GET, url, reqHeaders, AnyContentAsEmpty))
//    assert(res.header.status == 200)
//    val json = contentAsJson(fut(res))
//    (json \ "secure").as[Boolean]
    false
  }

//  def makeRequest[T: Writeable](req: Request[T]): Result =
//    await(route(app().application, req).get)
}

trait PlayAppSuite { self: munit.Suite =>
  val app: Fixture[TestComponents] = new Fixture[TestComponents]("pics-app") {
    private var comps: TestComponents = null
    def apply() = comps
    override def beforeAll(): Unit = {
      val db = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")
      db.start()
      comps = new TestComponents(TestConf.createTestAppContext)
      Play.start(comps.application)
    }
    override def afterAll(): Unit = {
      Play.stop(comps.application)
    }
  }

  override def munitFixtures = Seq(app)
}
