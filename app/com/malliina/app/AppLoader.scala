package com.malliina.app

import com.malliina.oauth.{GoogleOAuthCredentials, GoogleOAuthReader}
import com.malliina.pics.{BucketFiles, BucketName, PicFiles, Resizer}
import com.malliina.play.app.DefaultApp
import controllers.{Admin, AssetsComponents, Home}
import play.api.ApplicationLoader.Context
import play.api.cache.Cached
import play.api.cache.ehcache.EhCacheComponents
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, NoHttpFiltersComponents}
import router.Routes

class AppLoader extends DefaultApp(ctx => new AppComponents(
  ctx,
  GoogleOAuthReader.load,
  BucketFiles.forBucket(BucketName("malliina-pics")),
  BucketFiles.forBucket(BucketName("malliina-pics-thumbs"))
))

class AppComponents(context: Context, creds: GoogleOAuthCredentials, pics: PicFiles, thumbs: PicFiles)
  extends BuiltInComponentsFromContext(context)
    with NoHttpFiltersComponents
    with EhCacheComponents
    with AssetsComponents {

  val resizer = Resizer(maxWidth = 400, maxHeight = 300)
  lazy val cache = new Cached(defaultCacheApi)

  lazy val admin = new Admin(creds, controllerComponents.actionBuilder)
  lazy val home = new Home(pics, thumbs, resizer, admin, cache, Home.security(admin, materializer), controllerComponents)

  override val router: Router = new Routes(httpErrorHandler, home, assets, admin)
}
