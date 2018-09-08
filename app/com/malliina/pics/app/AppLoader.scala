package com.malliina.pics.app

import java.nio.file.Paths

import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.pics.CSRFConf.{CsrfCookieName, CsrfHeaderName, CsrfTokenName, CsrfTokenNoCheck}
import com.malliina.pics._
import com.malliina.pics.auth.{PicsAuth, PicsAuthLike, PicsAuthenticator}
import com.malliina.pics.db.PicsDatabase
import com.malliina.pics.html.PicsHtml
import com.malliina.play.app.DefaultApp
import com.typesafe.config.ConfigFactory
import controllers.Social.SocialConf
import controllers._
import play.api.ApplicationLoader.Context
import play.api.cache.Cached
import play.api.cache.ehcache.EhCacheComponents
import play.api.http.HttpConfiguration
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Configuration, Logger, Mode}
import play.filters.HttpFiltersComponents
import play.filters.csrf.CSRFConfig
import play.filters.headers.SecurityHeadersConfig
import play.filters.hosts.AllowedHostsConfig
import router.Routes

import scala.concurrent.Future

object LocalConf {
  val localConfFile = Paths.get(sys.props("user.home")).resolve(".pics/pics.conf")
  val localConf = Configuration(ConfigFactory.parseFile(localConfFile.toFile))
}

class AppLoader extends DefaultApp(ctx => new AppComponents(ctx, conf => GoogleOAuthCredentials(conf).fold(err => throw new Exception(err.message), identity)))

class AppComponents(context: Context, creds: Configuration => GoogleOAuthCredentials)
  extends BaseComponents(context, creds) {
  override lazy val httpErrorHandler = PicsErrorHandler

  override def buildAuthenticator() = PicsAuthenticator(JWTAuth.default, PicsAuth.social)

  override def buildPics() = MultiSizeHandler.default(materializer)
}

abstract class BaseComponents(context: Context, creds: Configuration => GoogleOAuthCredentials)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with EhCacheComponents
    with AssetsComponents {

  private val log = Logger(getClass)
  override val configuration = context.initialConfiguration ++ LocalConf.localConf

  def buildAuthenticator(): PicsAuthLike

  def buildPics(): MultiSizeHandler

  val mode = environment.mode

  lazy val socialConf = SocialConf(configuration)

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
  val html = PicsHtml.build(mode == Mode.Prod)
  val db: PicsDatabase = PicsDatabase.forMode(mode, configuration)
  db.init()
  val service = PicService(db, buildPics())
  log.info(s"Using pics dir '${FilePics.picsDir}'.")
  val cache = new Cached(defaultCacheApi)
  override lazy val httpErrorHandler = PicsErrorHandler
  val authenticator = buildAuthenticator()
  val auth = new PicsAuth(authenticator, materializer, defaultActionBuilder)
  val sockets = Sockets(auth, actorSystem, materializer)
  val pics = new PicsController(html, service, sockets, auth, cache, controllerComponents)
  val cognitoControl = CognitoControl.pics(defaultActionBuilder)
  val picsAssets = new PicsAssets(assets)
  lazy val social = Social.buildOrFail(defaultActionBuilder, socialConf)
  override lazy val router: Router = new Routes(httpErrorHandler, pics, sockets, picsAssets, social, cognitoControl)

  applicationLifecycle.addStopHook { () =>
    Future.successful(db.database.close())
  }
}
