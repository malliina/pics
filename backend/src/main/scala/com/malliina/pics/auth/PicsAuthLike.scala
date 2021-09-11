package com.malliina.pics.auth

import com.malliina.values.AccessToken
import com.malliina.web.{AuthError, JWTUser}

import scala.concurrent.Future

trait PicsAuthLike:
  def validateToken(token: AccessToken): Future[Either[AuthError, JWTUser]]
