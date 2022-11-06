package com.malliina.pics.http4s

import com.malliina.pics.{Access, Errors, SingleError}
import com.malliina.values.Readable
import io.circe.*
import io.circe.generic.semiauto.*
import org.http4s.UrlForm

class FormReader(form: UrlForm):
  def read[T](key: String)(implicit r: Readable[T]): Either[Errors, T] =
    r.read(key).left.map(err => Errors(SingleError.input(err.message)))

case class AccessLevel(access: Access)

object AccessLevel:
  implicit val json: Codec[AccessLevel] = deriveCodec[AccessLevel]

object Forms:
  def access(form: FormReader): Either[Errors, AccessLevel] =
    form.read[Access](Access.FormKey).map(AccessLevel.apply)
