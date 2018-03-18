package com.malliina.play.auth

object CognitoValidators {
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
}
