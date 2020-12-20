package com.malliina.pics.db

import com.malliina.pics.{Keys, PicOwner}
import tests.{BaseSuite, DoobieSuite}

class DoobiePicsDatabaseTests extends BaseSuite with DoobieSuite {
  test("can CRUD pic meta") {
    val data = doobie()
    val picsDatabase = DoobiePicsDatabase(data)
    val user = PicOwner("testuser")
    val key = Keys.randomish()
    val _ = picsDatabase.saveMeta(key, user).unsafeRunSync()
    val pics = picsDatabase.load(0, 2, user).unsafeRunSync()
    assert(pics.exists(_.key == key))
    assert(picsDatabase.contains(key).unsafeRunSync())
    picsDatabase.remove(key, user).unsafeRunSync()
    assert(!picsDatabase.contains(key).unsafeRunSync())
  }
}
