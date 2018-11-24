package com.malliina.pics

import java.nio.file.Path

import com.malliina.concurrent.Execution
import com.malliina.pics.db.PicsDatabase
import com.malliina.play.auth.CodeValidator
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain, DefaultCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse}

import scala.concurrent.{ExecutionContext, Future, Promise}

object BucketFiles2 {
  def apply(v1: BucketFiles): BucketFiles2 = {
    val creds = AwsCredentialsProviderChain.of(
      ProfileCredentialsProvider.create("pimp"),
      DefaultCredentialsProvider.create()
    )
    val client = S3AsyncClient.builder()
      .region(Region.EU_WEST_1)
      .credentialsProvider(creds)
      .build()
    new BucketFiles2(v1.bucket, client, v1)(Execution.cached)
  }

  def forBucket(bucket: BucketName) = apply(BucketFiles.forBucket(bucket))
}

class BucketFiles2(bucket: BucketName, v2: S3AsyncClient, v1: BucketFiles)(implicit ec: ExecutionContext) extends DataSource {
  val downloadsDir = PicsDatabase.tmpDir.resolve("downloads")

  override def get(key: Key): Future[DataResponse] = {
    val random = CodeValidator.randomString().take(8)
    val dest = downloadsDir.resolve(s"$key-$random.jpeg")
    val cf = v2.getObject(GetObjectRequest.builder().bucket(bucket.name).key(key.key).build(), dest)
    val p = Promise[GetObjectResponse]
    cf.whenComplete((r, t) => Option(t).fold(p.success(r))(t => p.failure(t)))
    p.future.map(_ => DataFile(dest))
  }

  override def load(from: Int, until: Int) = v1.load(from, until)

  def getStream(key: Key): Future[DataStream] = v1.getStream(key)

  /** Removes `key`.
    *
    * @param key key to delete
    * @return success even if `key` does not exist
    */
  override def remove(key: Key) = v1.remove(key)

  override def saveBody(key: Key, file: Path) = v1.saveBody(key, file)

  override def contains(key: Key) = v1.contains(key)
}
