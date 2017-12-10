package com.malliina.pics

import java.nio.file.Path

import akka.stream.scaladsl.StreamConverters
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.malliina.storage.StorageLong

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future
import scala.util.Try

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
    fut(aws.doesObjectExist(bucketName, key.key))

  override def get(key: Key): Future[DataResponse] = {
    val obj = aws.getObject(bucketName, key.key)
    val meta = obj.getObjectMetadata
    fut {
      DataStream(
        StreamConverters.fromInputStream(() => obj.getObjectContent),
        Option(meta.getContentLength).map(_.bytes),
        Option(meta.getContentType).map(ContentType.apply)
      )
    }
  }

  override def saveBody(key: Key, file: Path): Future[Unit] =
    Future.fromTry(Try(aws.putObject(bucketName, key.key, file.toFile)))

  override def remove(key: Key): Future[Unit] =
    Future.fromTry(Try(aws.deleteObject(bucketName, key.key)))
}

object BucketFiles {
  val Prod = BucketFiles.forBucket(BucketName("malliina-pics"))
  val Thumbs = BucketFiles.forBucket(BucketName("malliina-pics-thumbs"))

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
