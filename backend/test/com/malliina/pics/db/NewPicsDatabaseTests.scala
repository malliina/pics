package com.malliina.pics.db

import com.malliina.concurrent.Execution
import com.malliina.pics.{Keys, PicOwner}
import tests.{BaseSuite, MUnitDatabaseSuite, TestConf}

class NewPicsDatabaseTests extends BaseSuite with MUnitDatabaseSuite {
  test("can CRUD pic meta") {
    val container = db()
    val conf = TestConf(container)
    val picsDatabase = NewPicsDatabase.withMigrations(conf, Execution.cached)
    val user = PicOwner("testuser")
    val key = Keys.randomish()
    val _ = await(picsDatabase.saveMeta(key, user))
    val pics = await(picsDatabase.load(0, 2, user))
    assert(pics.exists(_.key == key))
    assert(await(picsDatabase.contains(key)))
    await(picsDatabase.remove(key, user))
    assert(!await(picsDatabase.contains(key)))
  }
}
