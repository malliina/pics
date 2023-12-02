package com.malliina.pics

import com.malliina.config.{ConfigError, ConfigNode, ConfigReadable, Env}
import com.malliina.database.Conf
import com.malliina.pics.app.LocalConf
import com.malliina.pics.auth.{SecretKey, SignInWithApple, Social}
import com.malliina.values.{ErrorMessage, Password}
import com.malliina.web.{AuthConf, ClientId, ClientSecret}

import java.nio.file.Path

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
  isProdBuild: Boolean,
  app: AppConf,
  db: Conf,
  google: AuthConf,
  github: AuthConf,
  microsoft: AuthConf,
  facebook: AuthConf,
  twitter: AuthConf,
  amazon: AuthConf,
  apple: Option[SignInWithApple.Conf]
):
  def social = Social.SocialConf(
    github,
    microsoft,
    google,
    facebook,
    twitter,
    amazon,
    apple
  )

object PicsConf:
  private val envName = Env.read[String]("ENV_NAME")
  private val isStaging = envName.contains("staging")

  given ConfigReadable[SecretKey] = ConfigReadable.string.map(s => SecretKey(s))

  def picsConf = LocalConf.config.parse[ConfigNode]("pics").toOption.get

  def unsafeLoad(c: ConfigNode = picsConf): PicsConf =
    unsafeLoadWith(
      c,
      c.parse[Password]("db.pass")
        .map: pass =>
          if BuildInfo.isProd then prodDatabaseConf(pass, if isStaging then 2 else 5)
          else devDatabaseConf(pass)
    )

  def unsafeLoadWith(c: ConfigNode, db: Either[ConfigError, Conf]): PicsConf =
    load(c, db).fold(err => throw new IllegalArgumentException(err.message.message), identity)

  def load(root: ConfigNode, db: Either[ConfigError, Conf]) =
    for
      secret <-
        if BuildInfo.isProd then root.parse[SecretKey]("app.secret")
        else root.opt[SecretKey]("app.secret").map(_.getOrElse(SecretKey.dev))
      dbConf <- db
      googleWebSecret <- root.parse[ClientSecret]("google.web.secret")
      githubSecret <- root.parse[ClientSecret]("github.client.secret")
      microsoftSecret <- root.parse[ClientSecret]("microsoft.client.secret")
      facebookSecret <- root.parse[ClientSecret]("facebook.client.secret")
      twitterSecret <- root.parse[ClientSecret]("twitter.client.secret")
      amazonSecret <- root.parse[ClientSecret]("amazon.client.secret")
      siwaPrivateKey <- root.opt[Path]("apple.signin.privateKey")
    yield
      val isProdBuild = BuildInfo.isProd
      PicsConf(
        isProdBuild,
        AppConf(secret),
        dbConf,
        Social.google(googleWebSecret),
        Social.github(githubSecret),
        Social.microsoft(microsoftSecret),
        Social.facebook(facebookSecret),
        Social.twitter(twitterSecret),
        Social.amazon(amazonSecret),
        siwaPrivateKey.map(key => siwaConf(key))
      )

  private def prodDatabaseConf(password: Password, maxPoolSize: Int) = Conf(
    "jdbc:mysql://database8-nuqmhn2cxlhle.mysql.database.azure.com:3306/pics",
    "pics",
    password.pass,
    Conf.MySQLDriver,
    maxPoolSize,
    autoMigrate = true
  )

  private def devDatabaseConf(password: Password) = Conf(
    "jdbc:mysql://localhost:3307/pics",
    "pics",
    password.pass,
    Conf.MySQLDriver,
    2,
    autoMigrate = false
  )

  private def siwaConf(privateKey: Path) = SignInWithApple.Conf(
    privateKey,
    "KYBY5Q5QD3",
    "D2T2QC36Z9",
    ClientId("com.malliina.pics.client")
  )
