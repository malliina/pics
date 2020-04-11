package tests

import akka.actor.ActorSystem
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.malliina.pics.s3.AsyncS3Bucket
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider

import scala.concurrent.Future
import scala.jdk.CollectionConverters.ListHasAsScala

class AwsTests extends munit.FunSuite {
  val bucketName = "malliina-pics-test-bucket2"
  val objectKey = "myKey"
  val testContent = "test content"

  implicit val as = ActorSystem("test")
  implicit val ec = as.dispatcher

  test("read profile".ignore) {
    ProfileCredentialsProvider.create("pimp").resolveCredentials()
  }

  test("list images".ignore) {
    val src = AsyncS3Bucket.Original
    src.load(0, 1000).foreach { key =>
      //      val content = src.get(key)
      //      val newKey = Key(key.key + ".jpg")
      //      println(s"$key ct ${content.contentType}")
    }
  }

  test("create bucket, save file, delete bucket".ignore) {
    withBucket { aws =>
      aws.putObject(bucketName, objectKey, testContent)

      val objs = aws.listObjects(bucketName)
      objs.getObjectSummaries.asScala foreach { file =>
        val obj = aws.getObject(bucketName, file.getKey)
        // Source closes the InputStream upon completion
        val contentSource = StreamConverters.fromInputStream(() => obj.getObjectContent)
        val contentConcat: Future[ByteString] = contentSource.runFold(ByteString.empty)(_ ++ _)
        val content = await(contentConcat)
        assert(content.utf8String == testContent)
      }

      aws.deleteObject(bucketName, objectKey)
    }
  }

  def withBucket[T](code: AmazonS3 => T) = {
    val aws: AmazonS3 = AmazonS3ClientBuilder.standard().withRegion(Regions.EU_WEST_1).build()
    if (!aws.doesBucketExistV2(bucketName))
      aws.createBucket(bucketName)
    try {
      code(aws)
    } finally {
      //aws.deleteBucket(bucketName)
    }
  }

}
