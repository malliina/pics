package tests

import com.malliina.pics.{BucketFiles, Syncer}
import com.malliina.pics.db.{PicsDatabase, PicsMetaDatabase}
import org.scalatest.FunSuite

class SyncTests extends FunSuite {
  ignore("can sync") {
    val db = PicsDatabase.prod()
    db.init()
    val pics = PicsMetaDatabase(db)
    await(Syncer.sync(BucketFiles.Original, pics))
  }
}
