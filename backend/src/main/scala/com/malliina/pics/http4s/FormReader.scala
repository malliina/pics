package com.malliina.pics.http4s

import com.malliina.pics.{Errors, SingleError}
import com.malliina.values.{ErrorMessage, Readable}
import org.http4s.UrlForm

class FormReader(form: UrlForm):
  def read[T](key: String)(using r: Readable[T]): Either[Errors, T] =
    FormReadable[T].read(key, form).left.map(err => Errors(SingleError.input(err.message)))

trait FormReadable[T]:
  def read(key: String, form: UrlForm): Either[ErrorMessage, T]

  def emap[U](f: T => Either[ErrorMessage, U]): FormReadable[U] = (key: String, form: UrlForm) =>
    read(key, form).flatMap(f)

object FormReadable:
  val string: FormReadable[String] = (key: String, form: UrlForm) =>
    form.getFirst(key).toRight(ErrorMessage(s"Not found: '$key'."))

  def apply[T](using fr: FormReadable[T]): FormReadable[T] = fr

  given option[T](using r: Readable[T]): FormReadable[Option[T]] =
    (key: String, form: UrlForm) =>
      form.getFirst(key).fold(Right(None))(s => r.read(s).map(Option.apply))

  given readable[T](using r: Readable[T]): FormReadable[T] =
    string.emap(str => r.read(str))
