package com.malliina.pics.s3

import java.nio.file.{Files, Path}

import cats.effect.IO
import com.malliina.pics.s3.S3Source.log
import com.malliina.pics.{BucketName, DataFile, DataSourceT, FilePicsIO, FlatMeta, Key, PicResult, PicSuccess, Util}
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.util.AppLogger
import com.malliina.web.Utils.randomString
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model._

import scala.jdk.CollectionConverters.CollectionHasAsScala

object S3Source {
  private val log = AppLogger(getClass)

  def apply(bucket: BucketName): S3Source = {
    val s3Client = S3AsyncClient
      .builder()
      .region(Region.EU_WEST_1)
      .build()
    val client = S3BucketIO(s3Client)
    client.createIfNotExists(bucket).unsafeRunSync()
    new S3Source(bucket, s3Client)
  }

  val Small = apply(BucketName("malliina-pics-small"))
  val Medium = apply(BucketName("malliina-pics-medium"))
  val Large = apply(BucketName("malliina-pics-large"))
  val Original = apply(BucketName("malliina-pics"))
}

class S3Source(bucket: BucketName, client: S3AsyncClient) extends DataSourceT[IO] {
  val bucketName = bucket.name
  val downloadsDir = FilePicsIO.tmpDir.resolve("downloads")
  Files.createDirectories(downloadsDir)

  override def get(key: Key): IO[DataFile] = {
    val random = randomString().take(8)
    val dest = downloadsDir.resolve(s"$random-$key")
    val cf =
      client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key.key).build(), dest)
    cf.io.map(_ => DataFile(dest))
  }

  def load(from: Int, until: Int): IO[Seq[FlatMeta]] = {
    val size = until - from
    if (size <= 0)
      IO.pure(Nil)
    else
      listBatch(identity)
        .flatMap(first => loadAcc(until, first, Nil))
        .map(_.drop(from))
  }

  private def loadAcc(
    desiredSize: Int,
    current: ListObjectsV2Response,
    acc: Seq[FlatMeta]
  ): IO[Seq[FlatMeta]] = {
    val newAcc = acc ++ current.contents().asScala.map { obj =>
      FlatMeta(Key(obj.key()), obj.lastModified())
    }
    if (!current.isTruncated || newAcc.size >= desiredSize) {
      IO.pure(newAcc.take(desiredSize))
    } else {
      listBatch(_.continuationToken(current.nextContinuationToken())).flatMap { next =>
        loadAcc(desiredSize, next, newAcc)
      }
    }
  }

  private def listBatch(decorate: ListObjectsV2Request.Builder => ListObjectsV2Request.Builder) = {
    val builder = ListObjectsV2Request.builder().bucket(bucketName)
    client.listObjectsV2(decorate(builder).build()).io
  }

  def remove(key: Key): IO[PicResult] = {
    val req = DeleteObjectRequest.builder().bucket(bucketName).key(key.key).build()
    client.deleteObject(req).io.map(_ => PicSuccess)
  }

  def saveBody(key: Key, file: Path): IO[StorageSize] = {
    Util
      .timedIO {
        client
          .putObject(PutObjectRequest.builder().bucket(bucket.name).key(key.key).build(), file)
          .io
          .map(_ => Files.size(file).bytes)
      }
      .map { result =>
        val size = result.result
        log.info(s"Saved '$key' to '$bucketName', size $size in ${result.duration}.")
        size
      }
  }

  def contains(key: Key): IO[Boolean] =
    client
      .headObject(HeadObjectRequest.builder().bucket(bucket.name).key(key.key).build())
      .io
      .map(_ => true)
      .handleErrorWith(_ => IO.pure(false))
}
