package com.malliina.pics.db

import com.malliina.pics.{Keys, PicOwner}
import tests.DoobieSuite

class PicsDatabaseTests extends munit.CatsEffectSuite with DoobieSuite:
  test("can CRUD pic meta"):
    val data = doobie()
    val picsDatabase = PicsDatabase(data)
    val user = PicOwner("testuser")
    val key = Keys.randomish()
    for
      _ <- picsDatabase.saveMeta(key, user)
      pics <- picsDatabase.load(0, 2, user)
      _ = assert(pics.exists(_.key == key))
      _ <- assertIOBoolean(picsDatabase.contains(key))
      _ <- picsDatabase.remove(key, user)
      _ <- assertIO(picsDatabase.contains(key), false)
    yield ()
