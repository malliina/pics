package com.malliina.pics.db

import akka.actor.ActorSystem
import com.malliina.pics.{Keys, PicOwner}
import tests.{BaseSuite, MUnitDatabaseSuite, TestConf}

class NewPicsDatabaseTests extends BaseSuite with MUnitDatabaseSuite {
  test("can CRUD pic meta") {
    val container = db()
    val as = ActorSystem("test")
    val conf = TestConf(container)
    try {
      val picsDatabase = NewPicsDatabase.withMigrations(as, conf)
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
