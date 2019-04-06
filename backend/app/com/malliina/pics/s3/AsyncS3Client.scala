package com.malliina.pics.s3

import com.malliina.pics.BucketName
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, HeadBucketRequest}

import scala.concurrent.{ExecutionContext, Future}

object AsyncS3Client {
  def apply(client: S3AsyncClient, ec: ExecutionContext) = new AsyncS3Client(client)(ec)
}

class AsyncS3Client(client: S3AsyncClient)(implicit val ec: ExecutionContext) {
  def createIfNotExists(bucket: BucketName): Future[Unit] = exists(bucket).flatMap { doesExist =>
    if (doesExist) Future.successful(())
    else create(bucket).map(_ => ())
  }

  def create(bucket: BucketName) =
    client.createBucket(CreateBucketRequest.builder().bucket(bucket.name).build()).future

  def exists(bucket: BucketName): Future[Boolean] =
    client
      .headBucket(HeadBucketRequest.builder().bucket(bucket.name).build())
      .future
      .map(_ => true)
      .recover { case _ => false }
}
