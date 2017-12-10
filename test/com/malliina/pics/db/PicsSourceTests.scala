package com.malliina.pics.db

import com.malliina.pics.Key
import com.malliina.play.models.Username
import tests.BaseSuite

class PicsSourceTests extends BaseSuite {
  test("can insert picture") {
    val db = PicsSource.inMemory()
    val op = db.saveMeta(Key("test.jpg"), Username("test"))
    await(op)
  }
}
