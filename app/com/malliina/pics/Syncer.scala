package com.malliina.pics

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics.Syncer.log
import com.malliina.pics.db.PicsDb
import com.malliina.play.models.Username
import play.api.Logger

import scala.concurrent.Future

object Syncer extends Syncer {
  private val log = Logger(getClass)
}

class Syncer {
  def sync(from: FlatFiles, to: PicsDb, maxItems: Int = 1000000): Future[Int] = {
    from.load(0, maxItems).flatMap { keys =>
      log.info(s"Syncing ${keys.length} keys...")
      Future.traverse(keys)(key => to.putMeta(key.withUser(Username("sync"))))
        .map(_.sum)
    }
  }
}
