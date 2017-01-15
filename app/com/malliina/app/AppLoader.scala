package com.malliina.app

import com.malliina.pics.{BucketFiles, BucketName}
import com.malliina.play.app.DefaultApp
import controllers.{Admin, Assets, Home}
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponentsFromContext, Mode}
import play.api.cache.{Cached, EhCacheComponents}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import router.Routes

class AppLoader extends DefaultApp(new AppComponents(_))

class AppComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with EhCacheComponents
    with AhcWSComponents {

  lazy val assets = new Assets(httpErrorHandler)
  val secretService = SecretService
  val picService = BucketFiles.forBucket(BucketName("malliina-pics"))
  lazy val cache = new Cached(defaultCacheApi)

  lazy val admin = new Admin(materializer, environment.mode == Mode.Prod)
  lazy val home = new Home(picService, admin, cache, wsClient)

  override val router: Router = new Routes(httpErrorHandler, home, assets, admin)
}
