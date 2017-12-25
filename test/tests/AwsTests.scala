package tests

import java.nio.file.{Files, Path}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, StreamConverters}
import akka.util.ByteString
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.malliina.pics.{BucketFiles, ScrimageResizer}
import org.scalatest.FunSuite

import scala.collection.JavaConverters.asScalaBuffer
import scala.concurrent.Future

class AwsTests extends FunSuite {
  val bucketName = "malliina-pics-test-bucket2"
  val objectKey = "myKey"
  val testContent = "test content"

  implicit val mat = ActorMaterializer()(ActorSystem("test"))
  implicit val ec = mat.executionContext

  ignore("list images") {
    val src = BucketFiles.Original
    src.load(0, 1000).foreach { key =>
      //      val content = src.get(key)
      //      val newKey = Key(key.key + ".jpg")
      //      println(s"$key ct ${content.contentType}")
    }
  }

  ignore("resize original images") {
    def resize(resizer: ScrimageResizer, prefix: String): Path => Path = src => {
      val dest = Files.createTempFile(prefix, null)
      await(resizer.resizeFile(src, dest)).right
      dest
    }

    val resizerSmall = resize(ScrimageResizer.Small, "small")
    val resizerMedium = resize(ScrimageResizer.Medium, "medium")
    val resizerLarge = resize(ScrimageResizer.Large, "large")
    val src = BucketFiles.Original
    val smalls = BucketFiles.Small
    val mediums = BucketFiles.Medium
    val larges = BucketFiles.Large
    val task = src.load(0, 1000).flatMap { metas =>
      val resizeTasks = metas.map { meta =>
        src.getStream(meta.key).flatMap { orig =>
          if (orig.isImage) {
            val origFile = Files.createTempFile("orig", null)
            orig.source.runWith(FileIO.toPath(origFile)).flatMap { _ =>
              for {
                _ <- smalls.saveBody(meta.key, resizerSmall(origFile))
                _ <- mediums.saveBody(meta.key, resizerMedium(origFile))
                _ <- larges.saveBody(meta.key, resizerLarge(origFile))
              } yield {
                println(s"Resized ${meta.key}")
              }
            }
          } else {
            fut(())
          }
        }
      }
      Future.sequence(resizeTasks)
    }
    await(task)
  }

  ignore("create bucket, save file, delete bucket") {
    withBucket { aws =>
      aws.putObject(bucketName, objectKey, testContent)

      val objs = aws.listObjects(bucketName)
      asScalaBuffer(objs.getObjectSummaries) foreach { file =>
        val obj = aws.getObject(bucketName, file.getKey)
        // Source closes the InputStream upon completion
        val contentSource = StreamConverters.fromInputStream(() => obj.getObjectContent)
        val contentConcat: Future[ByteString] = contentSource.runFold(ByteString.empty)(_ ++ _)
        val content = await(contentConcat)
        assert(content.utf8String === testContent)
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
