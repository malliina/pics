package com.malliina.app

import akka.stream.Materializer
import com.malliina.oauth.{GoogleOAuthCredentials, GoogleOAuthReader}
import com.malliina.pics._
import com.malliina.play.app.DefaultApp
import controllers.{Admin, AssetsComponents, Home, PicsAssets}
import play.api.ApplicationLoader.Context
import play.api.cache.Cached
import play.api.cache.ehcache.EhCacheComponents
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logger, NoHttpFiltersComponents}
import router.Routes

class AppLoader extends DefaultApp(ctx => new AppComponents(
  ctx,
  GoogleOAuthReader.load,
  mat => FileCachingPics(FilePics.default(mat), BucketFiles.Prod),
  mat => FileCachingPics(FilePics.thumbs(mat), BucketFiles.Thumbs)
))

class AppComponents(context: Context, creds: GoogleOAuthCredentials, pics: Materializer => PicFiles, thumbs: Materializer => PicFiles)
  extends BuiltInComponentsFromContext(context)
    with NoHttpFiltersComponents
    with EhCacheComponents
    with AssetsComponents {
  private val log = Logger(getClass)
  log.info(s"Using pics dir '${FilePics.picsDir}'.")
  lazy val cache = new Cached(defaultCacheApi)

  lazy val admin = new Admin(creds, controllerComponents.actionBuilder)
  lazy val home = new Home(pics(materializer), thumbs(materializer), Resizer.Prod, admin, cache, Home.security(admin, materializer), controllerComponents)
  val picsAssets = new PicsAssets(assets)
  override val router: Router = new Routes(httpErrorHandler, home, picsAssets, admin)
}
