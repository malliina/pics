package com.malliina.pics.db

import akka.actor.ActorSystem
import com.dimafeng.testcontainers.{ForAllTestContainer, MySQLContainer}
import com.malliina.pics.{Keys, PicOwner}
import tests.{BaseSuite, DbTest, TestConf}

@DbTest
class NewPicsDatabaseTests extends BaseSuite with ForAllTestContainer {
  override val container = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")

  test("can CRUD pic meta") {
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
