package com.malliina.pics

import com.malliina.concurrent.Execution.cached
import com.malliina.pics.Syncer.log
import com.malliina.pics.auth.PicsAuth
import play.api.Logger

import scala.concurrent.Future

object Syncer extends Syncer {
  private val log = Logger(getClass)
}

class Syncer {
  def sync(from: DataSource, to: MetaSource, maxItems: Int = 1000000): Future[Int] = {
    val user = PicsAuth.AdminUser
    from.load(0, maxItems).flatMap { keys =>
      log.info(s"Syncing ${keys.length} keys...")
      Future
        .traverse(keys)(key => to.putMetaIfNotExists(key.withUser(user)))
        .map(_.sum)
    }
  }
}
