package com.malliina.pics.app

import com.malliina.oauth.{GoogleOAuthCredentials, GoogleOAuthReader}
import com.malliina.pics.CSRFConf.{CsrfCookieName, CsrfHeaderName, CsrfTokenName, CsrfTokenNoCheck}
import com.malliina.pics._
import com.malliina.pics.auth.{PicsAuth, PicsAuthLike, PicsAuthenticator}
import com.malliina.pics.db.PicsDatabase
import com.malliina.pics.html.PicsHtml
import com.malliina.play.app.DefaultApp
import controllers.Social.SocialConf
import controllers._
import play.api.ApplicationLoader.Context
import play.api.cache.Cached
import play.api.cache.ehcache.EhCacheComponents
import play.api.http.HttpConfiguration
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Logger, Mode}
import play.filters.HttpFiltersComponents
import play.filters.csrf.CSRFConfig
import play.filters.headers.SecurityHeadersConfig
import play.filters.hosts.AllowedHostsConfig
import router.Routes

import scala.concurrent.Future

class AppLoader extends DefaultApp(ctx => new AppComponents(ctx, GoogleOAuthReader.load, SocialConf()))

class AppComponents(context: Context, creds: GoogleOAuthCredentials, socialConf: SocialConf)
  extends BaseComponents(context, creds, socialConf) {
  override lazy val httpErrorHandler = PicsErrorHandler

  override def buildAuthenticator() = PicsAuthenticator(JWTAuth.default, htmlAuth)

  override def buildPics() = MultiSizeHandler.default(materializer)
}

abstract class BaseComponents(context: Context, creds: GoogleOAuthCredentials, socialConf: SocialConf)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with EhCacheComponents
    with AssetsComponents {

  def buildAuthenticator(): PicsAuthLike

  def buildPics(): MultiSizeHandler

  private val log = Logger(getClass)
  val mode = environment.mode

  val allowedCsp = Seq(
    "maxcdn.bootstrapcdn.com",
    "code.jquery.com",
    "cdnjs.cloudflare.com",
    "fonts.gstatic.com",
    "use.fontawesome.com",
    "fonts.googleapis.com"
  )
  val allowedEntry = allowedCsp.mkString(" ")

  val csp = s"default-src 'self' 'unsafe-inline' $allowedEntry; connect-src *; img-src 'self' data:;"
  override lazy val securityHeadersConfig = SecurityHeadersConfig(contentSecurityPolicy = Option(csp))
  override lazy val allowedHostsConfig = AllowedHostsConfig(Seq("localhost", "pics.malliina.com", "images.malliina.com"))

  val defaultHttpConf = HttpConfiguration.fromConfiguration(configuration, environment)
  override lazy val httpConfiguration =
    if (mode == Mode.Prod) defaultHttpConf.copy(session = defaultHttpConf.session.copy(domain = Option(".malliina.com")))
    else defaultHttpConf
  override lazy val csrfConfig = CSRFConfig(
    tokenName = CsrfTokenName,
    cookieName = Option(CsrfCookieName),
    headerName = CsrfHeaderName,
    shouldProtect = rh => !rh.headers.get(CsrfHeaderName).contains(CsrfTokenNoCheck)
  )

  override def httpFilters = Seq(csrfFilter, securityHeadersFilter, allowedHostsFilter)

  val html = PicsHtml.build(mode == Mode.Prod)
  val db: PicsDatabase =
    if (mode == Mode.Prod) PicsDatabase.prod()
    else if (mode == Mode.Dev) PicsDatabase.dev()
    else PicsDatabase.inMemory()
  db.init()
  val service = PicService(db, buildPics())
  log.info(s"Using pics dir '${FilePics.picsDir}'.")
  val cache = new Cached(defaultCacheApi)
  override lazy val httpErrorHandler = PicsErrorHandler
  val admin = new Admin(html, creds, defaultActionBuilder)
  val htmlAuth = PicsAuth.oauth(admin)
  val authenticator = buildAuthenticator()
  val auth = new PicsAuth(authenticator, materializer, defaultActionBuilder)
  val sockets = Sockets(auth, actorSystem, materializer)
  val pics = new PicsController(html, service, sockets, auth, cache, controllerComponents)
  val cognitoControl = CognitoControl.pics(defaultActionBuilder)
  val picsAssets = new PicsAssets(assets)
  val social = Social.buildOrFail(defaultActionBuilder, socialConf)
  override val router: Router = new Routes(httpErrorHandler, pics, admin, sockets, picsAssets, cognitoControl, social)

  applicationLifecycle.addStopHook { () =>
    Future.successful(db.database.close())
  }
}
