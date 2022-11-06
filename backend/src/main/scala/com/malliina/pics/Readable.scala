package com.malliina.pics

import com.malliina.values.{Email, ErrorMessage, UserId}

import scala.util.Try

trait Readable[T]:
  def apply(s: Option[String]): Either[Errors, T]
  def map[U](f: T => U): Readable[U] =
    (s: Option[String]) => Readable.this.apply(s).map(f)
  def flatMap[U](f: T => Either[Errors, U]): Readable[U] =
    (s: Option[String]) => Readable.this.apply(s).flatMap(f)

object Readable:
  implicit val string: Readable[String] = (opt: Option[String]) =>
    opt.toRight(Errors("Missing key."))
  implicit val long: Readable[Long] = string.flatMap { s =>
    Try(s.toLong).fold(err => Left(Errors(s"Invalid long: '$s'.")), l => Right(l))
  }
  implicit val int: Readable[Int] = string.flatMap { s =>
    Try(s.toInt).fold(err => Left(Errors(s"Invalid long: '$s'.")), l => Right(l))
  }
  implicit val boolean: Readable[Boolean] = string.flatMap {
    case "true"  => Right(true)
    case "false" => Right(false)
    case other   => Left(Errors(s"Invalid boolean: '$other'."))
  }
  implicit val userId: Readable[UserId] = from[Long, UserId](UserId.build)
  implicit val email: Readable[Email] = from[String, Email](Email.build)
  implicit val access: Readable[Access] =
    from[String, Access](s => Access.parse(s))

  implicit def option[T: Readable]: Readable[Option[T]] = (opt: Option[String]) =>
    opt.fold[Either[Errors, Option[T]]](Right(None))(str =>
      Readable[T].apply(Option(str)).map(t => Option(t))
    )

  def apply[T](implicit r: Readable[T]): Readable[T] = r

  def from[T, U](build: T => Either[ErrorMessage, U])(implicit tr: Readable[T]) =
    tr.flatMap(t => build(t).left.map(Errors(_)))
