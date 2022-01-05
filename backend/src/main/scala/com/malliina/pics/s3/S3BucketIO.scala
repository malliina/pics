package com.malliina.pics.s3

import cats.effect.IO
import com.malliina.pics.s3.S3BucketIO.log
import com.malliina.pics.BucketName
import com.malliina.util.AppLogger
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, CreateBucketResponse, HeadBucketRequest}

object S3BucketIO:
  private val log = AppLogger(getClass)

class S3BucketIO(client: S3AsyncClient):
  def createIfNotExists(bucket: BucketName): IO[Boolean] =
    exists(bucket).flatMap { doesExist =>
      if doesExist then IO.pure(true)
      else create(bucket).map(_ => true)
    }

  def create(bucket: BucketName): IO[CreateBucketResponse] =
    client.createBucket(CreateBucketRequest.builder().bucket(bucket.name).build()).io

  def exists(bucket: BucketName): IO[Boolean] =
    client
      .headBucket(HeadBucketRequest.builder().bucket(bucket.name).build())
      .io
      .map(_ => true)
      .handleErrorWith { e =>
        log.warn(s"Unable to make HEAD request to bucket '$bucket'.", e)
        IO.pure(false)
      }
