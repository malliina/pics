package com.malliina.app

import com.malliina.oauth.{GoogleOAuthCredentials, GoogleOAuthReader}
import com.malliina.pics.{BucketFiles, BucketName, PicFiles}
import com.malliina.play.app.DefaultApp
import controllers.{Admin, Assets, Home}
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.cache.{Cached, EhCacheComponents}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import router.Routes

class AppLoader extends DefaultApp(ctx => new AppComponents(
  ctx,
  GoogleOAuthReader.load,
  BucketFiles.forBucket(BucketName("malliina-pics")))
)

class AppComponents(context: Context, creds: GoogleOAuthCredentials, pics: PicFiles)
  extends BuiltInComponentsFromContext(context)
    with EhCacheComponents
    with AhcWSComponents {

  lazy val assets = new Assets(httpErrorHandler)
  lazy val cache = new Cached(defaultCacheApi)

  lazy val admin = new Admin(creds, materializer)
  lazy val home = new Home(pics, admin, cache, wsClient, Home.security(admin))

  override val router: Router = new Routes(httpErrorHandler, home, assets, admin)
}
