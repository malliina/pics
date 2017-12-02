package com.malliina.pics.auth

import com.malliina.play.models.Username

case class CognitoUser(username: Username, groups: Seq[String])
