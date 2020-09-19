package com.malliina.pics.db

import com.malliina.pics.{Keys, PicOwner}
import tests.{BaseSuite, DoobieSuite}

class DoobiePicsDatabaseTests extends BaseSuite with DoobieSuite {
  test("can CRUD pic meta") {
    val data = doobie()
    val picsDatabase = DoobiePicsDatabase(data)
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
