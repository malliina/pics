package com.malliina.pics.app

import akka.stream.Materializer
import com.malliina.oauth.{GoogleOAuthCredentials, GoogleOAuthReader}
import com.malliina.pics._
import com.malliina.pics.auth.PicsAuth
import com.malliina.pics.db.PicsDatabase
import com.malliina.pics.html.PicsHtml
import com.malliina.play.app.DefaultApp
import com.malliina.play.controllers.BaseSecurity
import controllers._
import play.api.ApplicationLoader.Context
import play.api.cache.Cached
import play.api.cache.ehcache.EhCacheComponents
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logger, Mode, NoHttpFiltersComponents}
import router.Routes

class AppLoader extends DefaultApp(ctx => new AppComponents(
  ctx,
  GoogleOAuthReader.load,
  mat => PicSources.prod(mat)
))

class AppComponents(context: Context,
                    creds: GoogleOAuthCredentials,
                    buildPics: Materializer => PicSources)
  extends BuiltInComponentsFromContext(context)
    with NoHttpFiltersComponents
    with EhCacheComponents
    with AssetsComponents {
  private val log = Logger(getClass)
  val html = PicsHtml.build(environment.mode == Mode.Prod)
  val db: PicsDatabase =
    if (environment.mode == Mode.Test) PicsDatabase.inMemory()
    else PicsDatabase.default()
  db.init()
  val service = PicService(db, buildPics(materializer))
  log.info(s"Using pics dir '${FilePics.picsDir}'.")
  val cache = new Cached(defaultCacheApi)
  override lazy val httpErrorHandler = PicsErrorHandler
  val admin = new Admin(html, creds, defaultActionBuilder)
  val htmlAuth = PicsController.auth(admin)
  val security = new BaseSecurity(defaultActionBuilder, htmlAuth, materializer)
  val auth = new PicsAuth(JWTAuth.default, htmlAuth, materializer, defaultActionBuilder)
  val sockets = Sockets(auth, actorSystem, materializer)
  val pics = new PicsController(html, service, sockets, auth, cache, controllerComponents)
  val picsAssets = new PicsAssets(assets)
  override val router: Router = new Routes(httpErrorHandler, pics, admin, sockets, picsAssets)
}
