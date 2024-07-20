package com.malliina.pics.http4s

import cats.effect.{Concurrent, Sync}
import cats.syntax.all.toFunctorOps
import cats.~>
import com.malliina.http.Errors
import com.malliina.http4s.BasicService
import com.malliina.pics.{CSRFConf, CSRFToken}
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceInstances
import org.http4s.server.middleware.CSRF
import org.http4s.{Response, Status}

object MyCSRF extends CirceInstances:
  def build[F[_]: Sync, G[_]: Concurrent](gf: G ~> F): F[CSRF[F, G]] =
    CSRF
      .generateSigningKey[F]()
      .map: key =>
        CSRF[F, G](key, _ => true)
          .withOnFailure(
            Response[G](Status.Forbidden)
              .withHeaders(BasicService.noCache)
              .withEntity(Errors.single("CSRF"))
          )
          .withCSRFCheck(
            CSRF.checkCSRFinHeaderAndForm[F, G](CSRFConf.CsrfTokenName, gf)
          )
          .withCookieName(CSRFConf.CsrfCookieName)
          .build

  def generate[F[_]: Sync](generator: CSRF[F, F]): F[CSRFToken] =
    generator.generateToken[F].map(CSRF.unlift).map(CSRFToken.apply)

  def toToken(t: CSRF.CSRFToken): CSRFToken = CSRFToken(CSRF.unlift(t))
