package com.malliina.play.auth

import cats.effect.IO
import com.malliina.http.{HttpClient, OkClient}
import com.malliina.pics.auth.GoogleTokenAuth
import com.malliina.web.*

object Validators:
  val ExpectedPicsGroup = "pics-group"

  val picsAccess = new CognitoAccessValidator(
    Seq(KeyConfs.cognitoAccess, KeyConfs.cognitoId),
    Issuer("https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_egi2PEe65"),
    ClientId("2rnqepv44epargdosba6nlg2t9")
  )

  val picsId = new CognitoIdValidator(
    Seq(KeyConfs.cognitoAccess, KeyConfs.cognitoId),
    Issuer("https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_egi2PEe65"),
    ClientId("2rnqepv44epargdosba6nlg2t9")
  )

  def google(http: HttpClient[IO]) = GoogleTokenAuth(
    ClientId("469087885456-hol73l5j9tur3oq9fb4c07hr0m4dibge.apps.googleusercontent.com"),
    ClientId("122390040180-78dau8o0fd6eelgfdhed6g2pj4hlh701.apps.googleusercontent.com"),
    http
  )
