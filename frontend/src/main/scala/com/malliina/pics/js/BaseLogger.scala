package com.malliina.pics.js

trait BaseLogger:
  def debug(message: String): Unit
  def info(message: String): Unit
  def error(t: Throwable): Unit

object BaseLogger:
  val noop: BaseLogger = apply(_ => (), _ => (), _ => ())
  val console: BaseLogger = apply(println, println, println)

  def apply(
    onDebug: String => Unit,
    onInfo: String => Unit,
    onError: Throwable => Unit
  ): BaseLogger =
    new BaseLogger:
      override def debug(message: String): Unit = onDebug(message)
      override def info(message: String): Unit = onInfo(message)
      override def error(t: Throwable): Unit = onError(t)
