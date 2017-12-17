package com.malliina.pics.app

import akka.stream.Materializer
import com.malliina.oauth.{GoogleOAuthCredentials, GoogleOAuthReader}
import com.malliina.pics._
import com.malliina.pics.db.{PicsDatabase, PicsSource}
import com.malliina.play.app.DefaultApp
import controllers.{Admin, AssetsComponents, Home, PicsAssets}
import play.api.ApplicationLoader.Context
import play.api.cache.Cached
import play.api.cache.ehcache.EhCacheComponents
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logger, Mode, NoHttpFiltersComponents}
import router.Routes

class AppLoader extends DefaultApp(ctx => new AppComponents(
  ctx,
  GoogleOAuthReader.load,
  mat => PicService.prod(mat)
))

class AppComponents(context: Context,
                    creds: GoogleOAuthCredentials,
                    pics: Materializer => PicService)
  extends BuiltInComponentsFromContext(context)
    with NoHttpFiltersComponents
    with EhCacheComponents
    with AssetsComponents {
  private val log = Logger(getClass)
  val db =
    if (environment.mode == Mode.Test) PicsDatabase.inMemory()
    else PicsDatabase.default()
  db.init()
  val picsDb = PicsSource(db)
  log.info(s"Using pics dir '${FilePics.picsDir}'.")
  val cache = new Cached(defaultCacheApi)
  override lazy val httpErrorHandler = PicsErrorHandler
  val admin = new Admin(creds, controllerComponents.actionBuilder)
  val picService = pics(materializer)
  val resizer = PicsResizer.default
  val home = new Home(
    picsDb, PicsResizer.default, pics(materializer),
    admin, cache, Home.security(admin, materializer),
    controllerComponents)
  val picsAssets = new PicsAssets(assets)
  override val router: Router = new Routes(httpErrorHandler, home, picsAssets, admin)
}
