package tests

import com.malliina.pics.db.{PicsDatabase, PicsMetaDatabase}
import com.malliina.pics.Syncer
import com.malliina.pics.s3.AsyncS3Bucket
import org.scalatest.FunSuite

class SyncTests extends FunSuite {
  ignore("can sync") {
    val db = PicsDatabase.mysqlFromEnvOrFail()
    db.init()
    val pics = PicsMetaDatabase(db)
    await(Syncer.sync(AsyncS3Bucket.Original, pics))
  }
}
