package tests

import com.malliina.concurrent.Execution
import com.malliina.pics.Syncer
import com.malliina.pics.db.NewPicsDatabase
import com.malliina.pics.s3.AsyncS3Bucket

class SyncTests extends munit.FunSuite {
  test("can sync".ignore) {
//    val pics = NewPicsDatabase.mysqlFromEnvOrFail(Execution.cached)
//    await(Syncer.sync(AsyncS3Bucket.Original, pics))
  }
}
