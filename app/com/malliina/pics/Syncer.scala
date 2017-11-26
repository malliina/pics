package com.malliina.pics

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics.Syncer.log
import com.malliina.pics.db.PicsDb
import play.api.Logger

import scala.concurrent.Future

object Syncer extends Syncer {
  private val log = Logger(getClass)
}

class Syncer {
  def sync(from: PicFiles, to: PicsDb, maxItems: Int = 1000000): Future[Int] = {
    from.load(0, maxItems).flatMap { keys =>
      log.info(s"Syncing ${keys.length} keys...")
      Future.traverse(keys)(key => to.putMeta(key)).map { _.sum }
    }
  }
}
