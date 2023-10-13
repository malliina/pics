package com.malliina.pics.http4s

import com.malliina.pics.{Access, Errors}
import io.circe.Codec

case class AccessLevel(access: Access) derives Codec.AsObject

object Forms:
  def access(form: FormReader): Either[Errors, AccessLevel] =
    form.read[Access](Access.FormKey).map(AccessLevel.apply)
