package com.malliina.pics.s3

import com.malliina.pics.BucketName
import play.api.Logger
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{
  CreateBucketRequest,
  CreateBucketResponse,
  HeadBucketRequest
}

import scala.concurrent.{ExecutionContext, Future}

object AsyncS3Client {
  def apply(client: S3AsyncClient, ec: ExecutionContext) = new AsyncS3Client(client)(ec)
}

class AsyncS3Client(client: S3AsyncClient)(implicit val ec: ExecutionContext) {
  private val log = Logger(getClass)

  def createIfNotExists(bucket: BucketName): Future[Boolean] = exists(bucket)
    .flatMap { doesExist =>
      if (doesExist) Future.successful(false)
      else create(bucket).map(_ => true)
    }
    .recover {
      case e =>
        log.warn(s"Failed to create bucket '$bucket'.", e)
        false
    }

  def create(bucket: BucketName): Future[CreateBucketResponse] =
    client.createBucket(CreateBucketRequest.builder().bucket(bucket.name).build()).future

  def exists(bucket: BucketName): Future[Boolean] =
    client
      .headBucket(HeadBucketRequest.builder().bucket(bucket.name).build())
      .future
      .map(_ => true)
      .recover {
        case e =>
          log.warn(s"Unable to make HEAD request to bucket '$bucket'.", e)
          false
      }
}
