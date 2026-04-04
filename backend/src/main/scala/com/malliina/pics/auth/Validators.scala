package com.malliina.pics.auth

import cats.effect.Sync
import com.malliina.http.HttpClient
import com.malliina.web.WebLiterals.issuer
import com.malliina.web.{ClientId, CognitoAccessValidator, CognitoIdValidator}

object Validators:
  val ExpectedPicsGroup = "pics-group"

  val picsAccess = CognitoAccessValidator(
    Seq(KeyConfs.cognitoAccess, KeyConfs.cognitoId),
    issuer"https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_egi2PEe65",
    ClientId.unsafe("2rnqepv44epargdosba6nlg2t9")
  )

  val picsId = CognitoIdValidator(
    Seq(KeyConfs.cognitoAccess, KeyConfs.cognitoId),
    issuer"https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_egi2PEe65",
    ClientId.unsafe("2rnqepv44epargdosba6nlg2t9")
  )

  def google[F[_]: Sync](http: HttpClient[F]) = GoogleTokenAuth.default(
    ClientId.unsafe("469087885456-hol73l5j9tur3oq9fb4c07hr0m4dibge.apps.googleusercontent.com"),
    ClientId.unsafe("122390040180-78dau8o0fd6eelgfdhed6g2pj4hlh701.apps.googleusercontent.com"),
    http
  )
