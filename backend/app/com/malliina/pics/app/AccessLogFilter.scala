package com.malliina.pics.app

import com.malliina.concurrent.Execution
import play.api.Logger
import play.api.mvc.{EssentialAction, EssentialFilter}

import scala.concurrent.ExecutionContext

object AccessLogFilter {
  def apply(ec: ExecutionContext = Execution.cached): AccessLogFilter = new AccessLogFilter()(ec)
}

class AccessLogFilter()(implicit ec: ExecutionContext) extends EssentialFilter {
  private val log = Logger(getClass)

  override def apply(next: EssentialAction): EssentialAction = EssentialAction { rh =>
    val start = System.currentTimeMillis()
    log.info(s"$rh")
    next(rh).map { r =>
      val durMs = System.currentTimeMillis() - start
      log.info(s"$rh status ${r.header.status} in $durMs ms")
      r
    }
  }
}
