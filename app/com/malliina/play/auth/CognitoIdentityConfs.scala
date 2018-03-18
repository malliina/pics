package com.malliina.play.auth

import com.malliina.http.FullUrl

object CognitoIdentityConfs {
  def pics = CognitoIdentityConf(
    "2rnqepv44epargdosba6nlg2t9",
    FullUrl("https", "pics.auth.eu-west-1.amazoncognito.com", ""),
    "aws.cognito.signin.user.admin email openid phone profile"
  )
}
