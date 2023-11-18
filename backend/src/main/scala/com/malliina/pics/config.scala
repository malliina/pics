package com.malliina.pics

import com.malliina.config.{ConfigError, ConfigNode, ConfigReadable}
import com.malliina.database.Conf
import com.malliina.pics.app.LocalConf
import com.malliina.pics.auth.{SecretKey, SignInWithApple, Social}
import com.malliina.values.ErrorMessage
import com.malliina.web.{AuthConf, ClientId, ClientSecret}

case class SocialConf(id: ClientId, secret: ClientSecret):
  def auth = AuthConf(id, secret)

object SocialConf:
  given ConfigReadable[SocialConf] = ConfigReadable.node.emap: obj =>
    for
      id <- obj.parse[ClientId]("id")
      secret <- obj.parse[ClientSecret]("secret")
    yield SocialConf(id, secret)

case class SocialClientConf(client: SocialConf):
  def conf = client.auth

object SocialClientConf:
  given ConfigReadable[SocialClientConf] = ConfigReadable.node.emap: obj =>
    obj.parse[SocialConf]("client").map(apply)

case class GoogleConf(web: SocialConf):
  def conf = web.auth

case class AppConf(secret: SecretKey)

sealed trait AppMode:
  def isProd = this == AppMode.Prod

object AppMode:
  case object Prod extends AppMode
  case object Dev extends AppMode

  given ConfigReadable[AppMode] = ConfigReadable.string.emapParsed:
    case "prod" => Right(Prod)
    case "dev"  => Right(Dev)
    case other  => Left(ErrorMessage("Must be 'prod' or 'dev'."))

case class PicsConf(
  mode: AppMode,
  app: AppConf,
  db: Conf,
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
  given ConfigReadable[SecretKey] = ConfigReadable.string.map(s => SecretKey(s))

  // ConfigFactory.load(LocalConf.config).resolve().getConfig("pics")
  def picsConf = LocalConf.config.parse[ConfigNode]("pics").toOption.get

  def unsafeLoad(c: ConfigNode = picsConf): PicsConf =
    unsafeLoadWith(c, c.parse[Conf]("db"))

  def unsafeLoadWith(c: ConfigNode, db: Either[ConfigError, Conf]): PicsConf =
    load(c, db).fold(err => throw new IllegalArgumentException(err.message.message), identity)

  def load(root: ConfigNode, db: Either[ConfigError, Conf]) =
    def client(name: String) = root.parse[SocialClientConf](name)
    for
      mode <- root.parse[AppMode]("mode")
      secret <- root.parse[SecretKey]("app.secret")
      database <- db
      google <- root.parse[SocialConf]("google.web")
      github <- client("github")
      microsoft <- client("microsoft")
      facebook <- client("facebook")
      twitter <- client("twitter")
      siwa <- root.parse[SignInWithApple.Conf]("apple.signin")
      amazon <- client("amazon")
    yield PicsConf(
      mode,
      AppConf(secret),
      database,
      GoogleConf(google),
      github,
      microsoft,
      facebook,
      twitter,
      siwa,
      amazon
    )
