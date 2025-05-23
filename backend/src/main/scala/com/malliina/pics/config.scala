package com.malliina.pics

import cats.effect.Sync
import com.malliina.config.{ConfigError, ConfigNode, ConfigReadable, Env}
import com.malliina.database.Conf
import com.malliina.http.UrlSyntax.url
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
    case _      => Left(ErrorMessage("Must be 'prod' or 'dev'."))

case class PicsConf(
  isTest: Boolean,
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
  def isFull = isTest || isProdBuild

object PicsConf:
  val mariaDbDriver = "org.mariadb.jdbc.Driver"

  private val envName = Env.read[String]("ENV_NAME")
  private val isStaging = envName.contains("staging")

  given ConfigReadable[SecretKey] = ConfigReadable.string.map(s => SecretKey(s))

  def loadConf(): Either[ConfigError, PicsConf] =
    for
      node <- LocalConf.config.parse[ConfigNode]("pics")
      conf <- load(
        node,
        pass =>
          if BuildInfo.isProd then prodDatabaseConf(pass, if isStaging then 2 else 5)
          else devDatabaseConf(pass),
        isTest = false
      )
    yield conf

  def loadF[F[_]: Sync] = Sync[F].fromEither(loadConf())

  def load(
    root: ConfigNode,
    dbConf: Password => Conf,
    isTest: Boolean
  ): Either[ConfigError, PicsConf] =
    for
      secret <-
        if BuildInfo.isProd then root.parse[SecretKey]("app.secret")
        else root.opt[SecretKey]("app.secret").map(_.getOrElse(SecretKey.dev))
      dbPass <- root.parse[Password]("db.pass")
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
        isTest,
        isProdBuild,
        AppConf(secret),
        dbConf(dbPass),
        Social.google(googleWebSecret),
        Social.github(githubSecret),
        Social.microsoft(microsoftSecret),
        Social.facebook(facebookSecret),
        Social.twitter(twitterSecret),
        Social.amazon(amazonSecret),
        siwaPrivateKey.map(key => siwaConf(key))
      )

  private def prodDatabaseConf(password: Password, maxPoolSize: Int) = Conf(
    url"jdbc:mariadb://localhost:3306/pics",
    "pics",
    password,
    mariaDbDriver,
    maxPoolSize,
    autoMigrate = true
  )

  private def devDatabaseConf(password: Password) = Conf(
    url"jdbc:mariadb://localhost:3307/pics",
    "pics",
    password,
    mariaDbDriver,
    2,
    autoMigrate = false
  )

  private def siwaConf(privateKey: Path) = SignInWithApple.Conf(
    privateKey,
    "KYBY5Q5QD3",
    "D2T2QC36Z9",
    ClientId("com.malliina.pics.client")
  )
