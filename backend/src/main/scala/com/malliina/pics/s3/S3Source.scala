package com.malliina.pics.s3

import cats.effect.{Async, Sync}
import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.malliina.pics.s3.S3Source.log
import com.malliina.pics.{BucketName, DataFile, DataSourceT, FilePicsIO, FlatMeta, Key, PicResult, PicSuccess, Util}
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.util.AppLogger
import com.malliina.web.Utils.randomString
import fs2.io.file.{Files, Path}
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*

import scala.jdk.CollectionConverters.CollectionHasAsScala

object S3Source:
  private val log = AppLogger(getClass)

  private def forBucket[F[_]: Async](bucket: BucketName): Resource[F, S3Source[F]] =
    val creds = DefaultCredentialsProvider.builder().profileName("pics").build()
    val s3Client = Sync[F].delay(
      S3AsyncClient
        .builder()
        .region(Region.EU_WEST_1)
        .credentialsProvider(creds)
        .build()
    )
    val clientRes = Resource.make(s3Client)(c => Sync[F].delay(c.close()))
    clientRes.evalMap: c =>
      val client = S3Bucket(c)
      client
        .createIfNotExists(bucket)
        .flatMap: _ =>
          default(bucket, c)
  def Small[F[_]: Async] = forBucket(BucketName("malliina-pics-small"))
  def Medium[F[_]: Async] = forBucket(BucketName("malliina-pics-medium"))
  def Large[F[_]: Async] = forBucket(BucketName("malliina-pics-large"))
  def Original[F[_]: Async] = forBucket(BucketName("malliina-pics"))

  def default[F[_]: {Async, Files}](bucket: BucketName, client: S3AsyncClient): F[S3Source[F]] =
    val downloadsDir = FilePicsIO.tmpDir.resolve("downloads")
    Files[F]
      .createDirectories(downloadsDir)
      .map: _ =>
        S3Source(bucket, client, downloadsDir)

class S3Source[F[_]: Async](bucket: BucketName, client: S3AsyncClient, downloadsDir: Path)
  extends DataSourceT[F]:
  val F = Async[F]
  val bucketName = bucket.name

  override def get(key: Key): F[DataFile] =
    val random = randomString().take(8)
    val dest = downloadsDir.resolve(s"$random-$key")
    val cf =
      client.getObject(
        GetObjectRequest.builder().bucket(bucketName).key(key.key).build(),
        dest.toNioPath
      )
    cf.io[F].flatMap(_ => DataFile(dest))

  def load(from: Int, until: Int): F[List[FlatMeta]] =
    val size = until - from
    if size <= 0 then F.pure(Nil)
    else
      listBatch(identity)
        .flatMap(first => loadAcc(until, first, Nil))
        .map(_.drop(from))

  private def loadAcc(
    desiredSize: Int,
    current: ListObjectsV2Response,
    acc: List[FlatMeta]
  ): F[List[FlatMeta]] =
    val newAcc = acc ++ current
      .contents()
      .asScala
      .flatMap: obj =>
        Key
          .build(obj.key())
          .toOption
          .map: key =>
            FlatMeta(key, obj.lastModified())
    if !current.isTruncated || newAcc.size >= desiredSize then F.pure(newAcc.take(desiredSize))
    else
      listBatch(_.continuationToken(current.nextContinuationToken())).flatMap: next =>
        loadAcc(desiredSize, next, newAcc)

  private def listBatch(decorate: ListObjectsV2Request.Builder => ListObjectsV2Request.Builder) =
    val builder = ListObjectsV2Request.builder().bucket(bucketName)
    client.listObjectsV2(decorate(builder).build()).io

  def remove(key: Key): F[PicResult] =
    val req = DeleteObjectRequest.builder().bucket(bucketName).key(key.key).build()
    client.deleteObject(req).io.map(_ => PicSuccess)

  def saveBody(key: Key, file: Path): F[StorageSize] =
    Util
      .timedIO:
        client
          .putObject(
            PutObjectRequest.builder().bucket(bucket.name).key(key.key).build(),
            file.toNioPath
          )
          .io
          .flatMap(_ => Files[F].size(file).map(_.bytes))
      .map: result =>
        val size = result.result
        log.info(s"Saved '$key' to '$bucketName', size $size in ${result.duration}.")
        size

  def contains(key: Key): F[Boolean] =
    client
      .headObject(HeadObjectRequest.builder().bucket(bucket.name).key(key.key).build())
      .io
      .map(_ => true)
      .handleErrorWith(_ => F.pure(false))
