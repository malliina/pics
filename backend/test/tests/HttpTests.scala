package tests

import com.malliina.concurrent.Execution
import com.malliina.pics.Pics
import play.api.libs.json.JsValue
import play.api.libs.ws.DefaultWSCookie
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.test.NoMaterializer

import scala.concurrent.Future

class HttpTests extends BaseSuite {
  ignore("list pics") {
    implicit val ec = Execution.cached
    val mat = NoMaterializer
    val client = StandaloneAhcWSClient()(mat)
    val sessionCookie = DefaultWSCookie("PLAY_SESSION", "")
    val req =
      client.url("https://pics.malliina.com/pics?f=json").withCookies(sessionCookie).get().map {
        r =>
          r.body[JsValue].as[Pics]
      }
    val pics = await(req)
    val start = System.currentTimeMillis()
    val statusReq = Future.traverse(pics.pics) { pic =>
      client.url(pic.small.url).get().map { r =>
        println(s"${System.currentTimeMillis() - start}\t${r.status}")
        r.status
      }
    }
    val statuses = await(statusReq)
    println(s"Length ${statuses.length}")
    println(statuses)
  }
}
