package tests

import akka.actor.ActorSystem
import com.malliina.pics.Syncer
import com.malliina.pics.db.NewPicsDatabase
import com.malliina.pics.s3.AsyncS3Bucket

class SyncTests extends munit.FunSuite {
  test("can sync".ignore) {
    val as = ActorSystem("test")
    val pics = NewPicsDatabase.mysqlFromEnvOrFail(as)
    await(Syncer.sync(AsyncS3Bucket.Original, pics))
  }
}
