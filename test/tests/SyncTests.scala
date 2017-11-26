package tests

import com.malliina.pics.{BucketFiles, Syncer}
import com.malliina.pics.db.{PicsDatabase, PicsDb}
import org.scalatest.FunSuite

class SyncTests extends FunSuite {
  test("can sync") {
    val db = PicsDatabase.default()
    db.init()
    val pics = PicsDb(db)
    await(Syncer.sync(BucketFiles.Prod, pics))
  }
}
