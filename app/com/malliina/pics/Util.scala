package com.malliina.pics

import scala.concurrent.duration.{DurationLong, FiniteDuration}

object Util {
  def timed[T](work: => T): (T, FiniteDuration) = {
    val start = System.currentTimeMillis()
    val t = work
    val end = System.currentTimeMillis()
    (t, (end - start).millis)
  }
}
