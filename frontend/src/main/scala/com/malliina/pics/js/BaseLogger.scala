package com.malliina.pics.js

trait BaseLogger:
  def info(message: String): Unit

  def error(t: Throwable): Unit

object BaseLogger:
  val noop: BaseLogger = apply(_ => (), _ => ())
  val console: BaseLogger = apply(msg => println(msg), t => println(t))

  def apply(onInfo: String => Unit, onError: Throwable => Unit): BaseLogger =
    new BaseLogger:
      override def info(message: String): Unit = onInfo(message)

      override def error(t: Throwable): Unit = onError(t)
