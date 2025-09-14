package com.malliina.logback

import cats.effect.{Resource, Sync}
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import ch.qos.logback.classic.Level
import com.malliina.http.HttpClient
import com.malliina.logstreams.client.LogstreamsUtils
import com.malliina.pics.BuildInfo

object AppLogging:
  val userAgent = s"Pics/${BuildInfo.version} (${BuildInfo.gitHash.take(7)})"

  def init(): Unit =
    LogbackUtils.init(
      levelsByLogger = Map(
        "org.http4s.ember.server.EmberServerBuilderCompanionPlatform" -> Level.OFF
      )
    )

  def resource[F[_]: Async](d: Dispatcher[F], http: HttpClient[F]): Resource[F, Boolean] =
    Resource.make(LogstreamsUtils.installIfEnabled("pics", userAgent, d, http))(_ =>
      Sync[F].delay(LogbackUtils.loggerContext.stop())
    )
