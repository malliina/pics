package com.malliina.pics

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics.Syncer.log
import com.malliina.pics.db.PicsMetaDatabase
import controllers.Admin
import play.api.Logger

import scala.concurrent.Future

object Syncer extends Syncer {
  private val log = Logger(getClass)
}

class Syncer {
  def sync(from: DataSource, to: PicsMetaDatabase, maxItems: Int = 1000000): Future[Int] = {
    val user = Admin.AdminUser
    from.load(0, maxItems).flatMap { keys =>
      log.info(s"Syncing ${keys.length} keys...")
      Future.traverse(keys)(key => to.putMeta(key.withUser(user)))
        .map(_.sum)
    }
  }
}
