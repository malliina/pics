package com.malliina.pics

import com.malliina.pics.app.LocalConf
import com.malliina.pics.auth.SecretKey
import com.malliina.pics.db.DatabaseConf
import com.malliina.web.{AuthConf, ClientId, ClientSecret}
import controllers.Social
import pureconfig._
import pureconfig.error.CannotConvert

case class SocialConf(id: ClientId, secret: ClientSecret) {
  def auth = AuthConf(id, secret)
}

case class SocialClientConf(client: SocialConf) {
  def conf = client.auth
}

case class GoogleConf(web: SocialConf) {
  def conf = web.auth
}

case class AppConf(secret: SecretKey)

sealed trait AppMode {
  def isProd = this == AppMode.Prod
}

object AppMode {
  case object Prod extends AppMode
  case object Dev extends AppMode

  implicit val reader: ConfigReader[AppMode] = ConfigReader.stringConfigReader.emap {
    case "prod" => Right(Prod)
    case "dev"  => Right(Dev)
    case other  => Left(CannotConvert(other, "AppMode", "Must be 'prod' or 'dev'."))
  }
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
  apple: SocialClientConf,
  amazon: SocialClientConf
) {
  def social = Social.SocialConf(
    github.conf,
    microsoft.conf,
    google.conf,
    facebook.conf,
    twitter.conf,
    amazon.conf,
    apple.conf
  )
}

case class WrappedConf(pics: PicsConf)

object PicsConf {
  import pureconfig.generic.auto.exportReader

  val load: PicsConf = ConfigObjectSource(Right(LocalConf.localConfig))
    .withFallback(ConfigSource.default)
    .loadOrThrow[WrappedConf]
    .pics
}
