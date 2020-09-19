package com.malliina.pics.app

import java.nio.file.Paths

import com.malliina.http.OkClient
import com.malliina.pics.CSRFConf.{CsrfCookieName, CsrfHeaderName, CsrfTokenName, CsrfTokenNoCheck}
import com.malliina.pics._
import com.malliina.pics.auth.{PicsAuth, PicsAuthLike, PicsAuthenticator}
import com.malliina.pics.db.{Conf, DoobieDatabase, DoobiePicsDatabase}
import com.malliina.pics.html.PicsHtml
import com.malliina.play.app.DefaultApp
import com.typesafe.config.ConfigFactory
import controllers.Social.SocialConf
import controllers._
import play.api.ApplicationLoader.Context
import play.api.cache.Cached
import play.api.cache.ehcache.EhCacheComponents
import play.api.http.{HttpConfiguration, HttpErrorHandler}
import play.api.routing.Router
import play.api.{BuiltInComponentsFromContext, Configuration, Mode}
import play.filters.HttpFiltersComponents
import play.filters.csrf.CSRFConfig
import play.filters.gzip.GzipFilter
import play.filters.headers.SecurityHeadersConfig
import play.filters.hosts.AllowedHostsConfig
import router.Routes

import scala.concurrent.Future

object LocalConf {
  val appDir = Paths.get(sys.props("user.home")).resolve(".pics")
  val localConfFile = appDir.resolve("pics.conf")
  val localConf = Configuration(ConfigFactory.parseFile(localConfFile.toFile))
}

class AppLoader extends DefaultApp(ctx => new AppComponents(ctx))

class AppComponents(context: Context)
  extends BaseComponents(
    context,
    c => Conf.fromConf(c).fold(err => throw new Exception(err), c => AppConf(c))
  ) {
  override lazy val httpErrorHandler = PicsErrorHandler
  override def buildAuthenticator() = PicsAuthenticator(JWTAuth.default(http), PicsAuth.social)
  override def buildPics() = MultiSizeHandler.default(materializer)
}

trait AppConf {
  def database: Conf
  def close(): Unit
}

object AppConf {
  def apply(conf: Conf) = new AppConf {
    override def database: Conf = conf
    override def close(): Unit = ()
  }
}

abstract class BaseComponents(
  context: Context,
  dbConf: Configuration => AppConf
) extends BuiltInComponentsFromContext(context)
  with HttpFiltersComponents
  with EhCacheComponents
  with AssetsComponents {
  val http = OkClient.default
  override val configuration = LocalConf.localConf.withFallback(context.initialConfiguration)
  override lazy val httpFilters =
    Seq(new GzipFilter(), csrfFilter, securityHeadersFilter, allowedHostsFilter)

  def buildAuthenticator(): PicsAuthLike
  def buildPics(): MultiSizeHandler

  val mode = environment.mode
  lazy val socialConf = SocialConf(configuration)
  val devCsp = if (mode == Mode.Dev) "localhost:10101" :: Nil else Nil
  val allowedCsp = Seq(
    "maxcdn.bootstrapcdn.com",
    "code.jquery.com",
    "cdnjs.cloudflare.com",
    "fonts.gstatic.com",
    "use.fontawesome.com",
    "fonts.googleapis.com"
  ) ++ devCsp
  val allowedEntry = allowedCsp.mkString(" ")
  val csps = Seq(
    s"default-src 'self' 'unsafe-inline' 'unsafe-eval' $allowedEntry;",
    "connect-src *;",
    "img-src 'self' data:;",
    "font-src 'self' data: fonts.gstatic.com use.fontawesome.com;"
  )
  val csp = csps.mkString
  override lazy val securityHeadersConfig = SecurityHeadersConfig(
    contentSecurityPolicy = Option(csp)
  )
  override lazy val allowedHostsConfig = AllowedHostsConfig(
    Seq("localhost", "pics.malliina.com", "images.malliina.com")
  )

  override lazy val csrfConfig = CSRFConfig(
    tokenName = CsrfTokenName,
    cookieName = Option(CsrfCookieName),
    headerName = CsrfHeaderName,
    shouldProtect = rh => {
      val hasCsrfHeader = rh.headers.get(CsrfHeaderName).contains(CsrfTokenNoCheck)
      val isAuthCallback = rh.path.startsWith("/sign-in/callbacks/")
      !hasCsrfHeader && !isAuthCallback
    }
  )

  val defaultHttpConf = HttpConfiguration.fromConfiguration(configuration, environment)
  // Sets sameSite = None, otherwise the Google auth redirect will wipe out the session state
  override lazy val httpConfiguration =
    defaultHttpConf.copy(
      session = defaultHttpConf.session.copy(cookieName = "picsSession", sameSite = None)
    )

  val html = PicsHtml.build(mode == Mode.Prod)
  val conf = dbConf(configuration)
  val db = DoobieDatabase.withMigrations(conf.database, executionContext)
  val picsDatabase = DoobiePicsDatabase(db)
  val service = PicService(picsDatabase, buildPics())
  val cache = new Cached(defaultCacheApi)
  override lazy val httpErrorHandler: HttpErrorHandler = PicsErrorHandler
  val authenticator = buildAuthenticator()
  val auth = new PicsAuth(authenticator, materializer, defaultActionBuilder)
  val sockets = Sockets(auth, actorSystem, materializer)
  val pics = new PicsController(html, service, sockets, auth, cache, controllerComponents)
  val cognitoControl = CognitoControl.pics(defaultActionBuilder)
  val picsAssets = new PicsAssets(assets)
  val social = Social.buildOrFail(socialConf, http, controllerComponents)
  override val router: Router =
    new Routes(httpErrorHandler, pics, sockets, picsAssets, social, cognitoControl)

  applicationLifecycle.addStopHook { () =>
    Future.successful {
      db.close()
      conf.close()
      http.close()
    }
  }
}
