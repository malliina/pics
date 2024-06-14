package com.malliina.pics

import com.malliina.values.ErrorMessage

import scala.quoted.{Expr, Quotes, quotes}

object Literals extends Literals

trait Literals:
  extension (i: Int) inline def nonNeg: NonNeg = ${ LiteralsSyntax.NonNegLiteral('i) }

private object LiteralsSyntax:
  object NonNegLiteral extends LiteralInt[NonNeg]:
    override def parse(in: Int)(using Quotes): Either[ErrorMessage, Expr[NonNeg]] =
      NonNeg(in).map: _ =>
        '{ NonNeg(${ Expr(in) }).getUnsafe }

  trait LiteralInt[T]:
    def parse(in: Int)(using Quotes): Either[ErrorMessage, Expr[T]]

    def apply(x: Expr[Int])(using Quotes): Expr[T] =
      val f = x.valueOrAbort
      parse(f)
        .fold(
          err =>
            quotes.reflect.report.error(err.message)
            ???
          ,
          ok => ok
        )

extension [T](e: Either[ErrorMessage, T])
  def getUnsafe: T = e.fold(err => throw IllegalArgumentException(err.message), identity)
