package com.malliina.pics.db

import cats.Show
import com.malliina.pics.{Access, Key, PicOwner}
import com.malliina.values.{ErrorMessage, NonNeg, Username, ValidatingCompanion}
import doobie.Meta

import java.time.Instant

trait DoobieMappings:
  given Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  given Meta[Key] = validated(Key)
  given Meta[PicOwner] = validated(PicOwner)
  given Meta[Username] = validated(Username)
  given Meta[Access] = validated(Access)
  given Meta[NonNeg] = validated(NonNeg)

  private def validated[T, R: {Meta, Show}, C <: ValidatingCompanion[R, T]](c: C): Meta[T] =
    Meta[R].tiemap(r => c.build(r).left.map(err => err.message))(c.write)

extension [T](e: Either[ErrorMessage, T])
  def getUnsafe: T = e.fold(err => throw IllegalArgumentException(err.message), identity)
