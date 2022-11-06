package com.malliina.pics.http4s

import com.malliina.pics.{Access, Errors}
import io.circe.*
import io.circe.generic.semiauto.*

case class AccessLevel(access: Access)

object AccessLevel:
  implicit val json: Codec[AccessLevel] = deriveCodec[AccessLevel]

object Forms:
  def access(form: FormReader): Either[Errors, AccessLevel] =
    form.readT[Access](Access.FormKey).map(AccessLevel.apply)
