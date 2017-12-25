package com.malliina.pics

import java.nio.file.{Files, Path}

import akka.stream.scaladsl.StreamConverters
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics.BucketFiles.log
import com.malliina.storage.StorageLong
import play.api.Logger

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future

class BucketFiles(val aws: AmazonS3, val bucket: BucketName) extends DataSource {
  val bucketName: String = bucket.name

  def copy(src: Key, dest: Key) = aws.copyObject(bucketName, src.key, bucketName, dest.key)

  def load(from: Int, until: Int): Future[Seq[FlatMeta]] = {
    val size = until - from
    if (size <= 0) fut(Nil)
    else fut(loadAcc(until, aws.listObjects(bucketName), Nil).drop(from))
  }

  private def loadAcc(desiredSize: Int, current: ObjectListing, acc: Seq[FlatMeta]): Seq[FlatMeta] = {
    val newAcc = acc ++ current.getObjectSummaries.asScala.map { s =>
      FlatMeta(Key(s.getKey), s.getLastModified.toInstant)
    }
    if (!current.isTruncated || newAcc.size >= desiredSize) newAcc take desiredSize
    else loadAcc(desiredSize, aws.listNextBatchOfObjects(current), newAcc)
  }

  override def contains(key: Key): Future[Boolean] =
    Future(aws.doesObjectExist(bucketName, key.key))

  override def get(key: Key): Future[DataResponse] =
    getStream(key)

  def getStream(key: Key): Future[DataStream] = {
    val obj = aws.getObject(bucketName, key.key)
    val meta = obj.getObjectMetadata
    Future {
      DataStream(
        StreamConverters.fromInputStream(() => obj.getObjectContent),
        Option(meta.getContentLength).map(_.bytes),
        Option(meta.getContentType).map(ContentType.apply)
      )
    }
  }

  override def saveBody(key: Key, file: Path): Future[Unit] = {
    Future {
      log.info(s"Saving '$key' to '$bucketName'...")
      val (_, duration) = Util.timed {
        aws.putObject(bucketName, key.key, file.toFile)
      }
      val size = Files.size(file).bytes
      log.info(s"Saved '$key' to '$bucketName', size $size in $duration.")
    }
  }

  override def remove(key: Key): Future[PicResult] =
    Future(aws.deleteObject(bucketName, key.key)).map(_ => PicSuccess)
}

object BucketFiles {
  private val log = Logger(getClass)

  val Original = BucketFiles.forBucket(BucketName("malliina-pics"))
  val Small = BucketFiles.forBucket(BucketName("malliina-pics-small"))
  val Medium = BucketFiles.forBucket(BucketName("malliina-pics-medium"))
  val Large = BucketFiles.forBucket(BucketName("malliina-pics-large"))

  def forBucket(bucket: BucketName) = forS3(Regions.EU_WEST_1, bucket)

  def forS3(region: Regions, bucket: BucketName): BucketFiles = {
    val bucketName = bucket.name
    val credentialsChain = new AWSCredentialsProviderChain(
      new ProfileCredentialsProvider("pimp"),
      DefaultAWSCredentialsProviderChain.getInstance()
    )
    new DefaultAWSCredentialsProviderChain()
    val aws = AmazonS3ClientBuilder.standard().withRegion(region).withCredentials(credentialsChain).build()
    if (!aws.doesBucketExistV2(bucketName))
      aws.createBucket(bucketName)
    new BucketFiles(aws, bucket)
  }
}
