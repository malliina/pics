package com.malliina.pics

import com.malliina.config.ConfigReadable
import com.malliina.pics.PicsConf.ConfigOps
import com.malliina.pics.app.LocalConf
import com.malliina.pics.auth.{SecretKey, SignInWithApple}
import com.malliina.pics.db.DatabaseConf
import com.malliina.values.ErrorMessage
import com.malliina.web.{AuthConf, ClientId, ClientSecret}
import com.typesafe.config.{Config, ConfigFactory}
import controllers.Social

case class SocialConf(id: ClientId, secret: ClientSecret):
  def auth = AuthConf(id, secret)

object SocialConf:
  implicit val config: ConfigReadable[SocialConf] = ConfigReadable.config.emap { obj =>
    for
      id <- obj.read[ClientId]("id")
      secret <- obj.read[ClientSecret]("secret")
    yield SocialConf(id, secret)
  }

case class SocialClientConf(client: SocialConf):
  def conf = client.auth

object SocialClientConf:
  implicit val config: ConfigReadable[SocialClientConf] = ConfigReadable.config.emap { obj =>
    obj.read[SocialConf]("client").map(apply)
  }

case class GoogleConf(web: SocialConf):
  def conf = web.auth

case class AppConf(secret: SecretKey)

sealed trait AppMode:
  def isProd = this == AppMode.Prod

object AppMode:
  case object Prod extends AppMode
  case object Dev extends AppMode

  implicit val reader: ConfigReadable[AppMode] = ConfigReadable.string.emap {
    case "prod" => Right(Prod)
    case "dev"  => Right(Dev)
    case other  => Left(ErrorMessage("Must be 'prod' or 'dev'."))
  }

case class PicsConf(
  mode: AppMode,
  app: AppConf,
  db: DatabaseConf,
  google: GoogleConf,
  github: SocialClientConf,
  microsoft: SocialClientConf,
  facebook: SocialClientConf,
  twitter: SocialClientConf,
  apple: SignInWithApple.Conf,
  amazon: SocialClientConf
):
  def social = Social.SocialConf(
    github.conf,
    microsoft.conf,
    google.conf,
    facebook.conf,
    twitter.conf,
    amazon.conf,
    apple
  )

object PicsConf:
  implicit val secret: ConfigReadable[SecretKey] = ConfigReadable.string.map(s => SecretKey(s))

  implicit class ConfigOps(c: Config) extends AnyVal:
    def read[T](key: String)(implicit r: ConfigReadable[T]): Either[ErrorMessage, T] =
      r.read(key, c)
    def unsafe[T: ConfigReadable](key: String): T =
      c.read[T](key).fold(err => throw new IllegalArgumentException(err.message), identity)
  def picsConf = ConfigFactory.load(LocalConf.config).resolve().getConfig("pics")

  def unsafeLoad(c: Config = picsConf): PicsConf = unsafeLoadWith(c, c.unsafe[DatabaseConf]("db"))

  def unsafeLoadWith(c: Config, db: => DatabaseConf): PicsConf =
    def client(name: String): SocialClientConf = c.unsafe[SocialClientConf](name)
    PicsConf(
      c.unsafe[AppMode]("mode"),
      AppConf(c.unsafe[Config]("app").unsafe[SecretKey]("secret")),
      db,
      GoogleConf(c.unsafe[Config]("google").unsafe[SocialConf]("web")),
      client("github"),
      client("microsoft"),
      client("facebook"),
      client("twitter"),
      c.unsafe[Config]("apple").unsafe[SignInWithApple.Conf]("signin"),
      client("amazon")
    )
