package tests

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import org.scalatest.FunSuite

import scala.collection.JavaConverters.asScalaBuffer
import scala.concurrent.Future

class AwsTests extends FunSuite {
  val bucketName = "malliina-pics-test-bucket2"
  val objectKey = "myKey"
  val testContent = "test content"

  implicit val mat = ActorMaterializer()(ActorSystem("test"))
  implicit val ec = mat.executionContext

  ignore("create bucket, save file, delete bucket") {
    withBucket { aws =>
      aws.putObject(bucketName, objectKey, testContent)

      val objs = aws.listObjects(bucketName)
      asScalaBuffer(objs.getObjectSummaries()) foreach { file =>
        val obj = aws.getObject(bucketName, file.getKey)
        // Source closes the InputStream upon completion
        val contentSource = StreamConverters.fromInputStream(() => obj.getObjectContent())
        val contentConcat: Future[ByteString] = contentSource.runFold(ByteString.empty)(_ ++ _)
        val content = await(contentConcat)
        assert(content.utf8String === testContent)
      }

      aws.deleteObject(bucketName, objectKey)
    }
  }

  //  test("controller") {
  //    val files = BucketFiles.forBucket(BucketName("malliina-test-ctrl-bucket"))
  //    val aws = files.aws
  //    val name = files.bucketName
  //    if (!aws.doesBucketExist(name))
  //      aws.createBucket(name)
  //    val ctrl = new Home(files)
  //    val acc: Accumulator[ByteString, Result] = ctrl.put.apply(FakeRequest().withBody("boo"))
  //    val result = await(acc.run())
  //    assert(result.header.headers.get(HeaderNames.LOCATION).isDefined)
  //  }

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
