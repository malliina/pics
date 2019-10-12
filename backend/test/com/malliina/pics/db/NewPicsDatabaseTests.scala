package com.malliina.pics.db

import akka.actor.ActorSystem
import com.malliina.pics.{Keys, PicOwner}
import org.flywaydb.core.Flyway
import tests.{BaseSuite, TestComps}

class NewPicsDatabaseTests extends BaseSuite {
  test("can CRUD pic meta") {
    val as = ActorSystem("test")
    try {
      val conf = TestComps.startTestDatabase()
      val flyway = Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load
      flyway.migrate()
      val picsDatabase = NewPicsDatabase(as, conf)
      import picsDatabase.ec
      val user = PicOwner("testuser")
      val key = Keys.randomish()
      val task = for {
        _ <- picsDatabase.saveMeta(key, user)
        pics <- picsDatabase.load(0, 2, user)
      } yield pics
      val pics = await(task)
      assert(pics.exists(_.key == key))
      assert(await(picsDatabase.contains(key)))
      await(picsDatabase.remove(key, user))
      assert(!await(picsDatabase.contains(key)))
    } finally {
      await(as.terminate())
    }
  }
}
