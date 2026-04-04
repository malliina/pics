package com.malliina.pics.db

import cats.Show
import com.malliina.pics.{Access, Key, Language, PicUsername, Role}
import com.malliina.values.{Email, ErrorMessage, NonNeg, Username, ValidatingCompanion}
import com.malliina.web.CognitoUserId
import doobie.Meta

import java.time.Instant

given Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
given Meta[Key] = validated(Key)
given Meta[PicUsername] = validated(PicUsername)
given Meta[Username] = validated(Username)
given Meta[Email] = validated(Email)
given Meta[Access] = validated(Access)
given Meta[NonNeg] = validated(NonNeg)
given Meta[Role] = validated(Role)
given Meta[Language] = validated(Language)
given Meta[CognitoUserId] = validated(CognitoUserId)

private def validated[T, R: {Meta, Show}, C <: ValidatingCompanion[R, T]](c: C): Meta[T] =
  Meta[R].tiemap(r => c.build(r).left.map(err => err.message))(c.write)

extension [T](e: Either[ErrorMessage, T])
  def getUnsafe: T = e.fold(err => throw IllegalArgumentException(err.message), identity)
