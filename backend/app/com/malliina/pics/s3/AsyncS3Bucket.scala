package com.malliina.pics.s3

import java.nio.file.{Files, Path}

import com.malliina.concurrent.Execution
import com.malliina.pics.db.PicsDatabase
import com.malliina.pics.s3.AsyncS3Bucket.log
import com.malliina.pics._
import com.malliina.play.auth.CodeValidator
import com.malliina.storage.{StorageLong, StorageSize}
import play.api.Logger
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain,
  DefaultCredentialsProvider,
  ProfileCredentialsProvider
}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model._

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object AsyncS3Bucket {
  private val log = Logger(getClass)

  def apply(bucket: BucketName): AsyncS3Bucket = {
    val creds = AwsCredentialsProviderChain.of(
      ProfileCredentialsProvider.create("pimp"),
      DefaultCredentialsProvider.create()
    )
    val client = S3AsyncClient
      .builder()
      .region(Region.EU_WEST_1)
      .credentialsProvider(creds)
      .build()
    val s3 = AsyncS3Client(client, Execution.cached)
    Await.result(s3.createIfNotExists(bucket), 10.seconds)
    new AsyncS3Bucket(bucket, client)(Execution.cached)
  }

  val Small = AsyncS3Bucket(BucketName("malliina-pics-small"))
  val Medium = AsyncS3Bucket(BucketName("malliina-pics-medium"))
  val Large = AsyncS3Bucket(BucketName("malliina-pics-large"))
  val Original = AsyncS3Bucket(BucketName("malliina-pics"))
}

class AsyncS3Bucket(bucket: BucketName, v2: S3AsyncClient)(implicit ec: ExecutionContext)
    extends DataSource {
  val bucketName = bucket.name
  val downloadsDir = PicsDatabase.tmpDir.resolve("downloads")
  Files.createDirectories(downloadsDir)

  override def get(key: Key): Future[DataFile] = {
    val random = CodeValidator.randomString().take(8)
    val dest = downloadsDir.resolve(s"$random-$key")
    val cf = v2.getObject(GetObjectRequest.builder().bucket(bucketName).key(key.key).build(), dest)
    cf.future.map(_ => DataFile(dest))
  }

  override def load(from: Int, until: Int): Future[Seq[FlatMeta]] = {
    val size = until - from
    if (size <= 0)
      Future.successful(Nil)
    else
      listBatch(identity)
        .flatMap(first => loadAcc(until, first, Nil))
        .map(_.drop(from))
  }

  private def loadAcc(
      desiredSize: Int,
      current: ListObjectsV2Response,
      acc: Seq[FlatMeta]
  ): Future[Seq[FlatMeta]] = {
    val newAcc = acc ++ current.contents().asScala.map { obj =>
      FlatMeta(Key(obj.key()), obj.lastModified())
    }
    if (!current.isTruncated || newAcc.size >= desiredSize) {
      fut(newAcc.take(desiredSize))
    } else {
      listBatch(_.continuationToken(current.nextContinuationToken())).flatMap { next =>
        loadAcc(desiredSize, next, newAcc)
      }
    }
  }

  private def listBatch(decorate: ListObjectsV2Request.Builder => ListObjectsV2Request.Builder) = {
    val builder = ListObjectsV2Request.builder().bucket(bucketName)
    v2.listObjectsV2(decorate(builder).build()).future
  }

  /** Removes `key`.
    *
    * @param key key to delete
    * @return success even if `key` does not exist
    */
  override def remove(key: Key): Future[PicResult] = {
    val req = DeleteObjectRequest.builder().bucket(bucketName).key(key.key).build()
    v2.deleteObject(req).future.map(_ => PicSuccess)
  }

  override def saveBody(key: Key, file: Path): Future[StorageSize] = {
    Util
      .runTimed {
        v2.putObject(PutObjectRequest.builder().bucket(bucket.name).key(key.key).build(), file)
          .future
          .map(_ => Files.size(file).bytes)
      }
      .map { result =>
        val size = result.result
        log.info(s"Saved '$key' to '$bucketName', size $size in ${result.duration}.")
        size
      }

  }

  // The AWS docs suggest it should fail with a NoSuchKeyException if the key doesn't exist, but it fails with
  // CompletionException: S3Exception: null (Service: S3, Status Code: 404, Request ID: null)
  override def contains(key: Key): Future[Boolean] =
    v2.headObject(HeadObjectRequest.builder().bucket(bucket.name).key(key.key).build())
      .future
      .map(_ => true)
      .recover { case _ => false }
}
