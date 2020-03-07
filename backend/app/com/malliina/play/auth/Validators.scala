package com.malliina.play.auth

import com.malliina.http.OkClient
import com.malliina.pics.auth.GoogleTokenAuth

object Validators {
  val ExpectedPicsGroup = "pics-group"

  val picsAccess = new CognitoAccessValidator(
    Seq(KeyConfs.cognitoAccess, KeyConfs.cognitoId),
    "https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_egi2PEe65",
    "2rnqepv44epargdosba6nlg2t9"
  )

  val picsId = new CognitoIdValidator(
    Seq(KeyConfs.cognitoAccess, KeyConfs.cognitoId),
    "https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_egi2PEe65",
    "2rnqepv44epargdosba6nlg2t9"
  )

  def google(http: OkClient) = GoogleTokenAuth(
    "469087885456-hol73l5j9tur3oq9fb4c07hr0m4dibge.apps.googleusercontent.com",
    "122390040180-78dau8o0fd6eelgfdhed6g2pj4hlh701.apps.googleusercontent.com",
    http
  )
}
