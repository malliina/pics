package com.malliina.pics.s3

import cats.effect.{Async, Sync}
import cats.syntax.all.*
import com.malliina.pics.s3.S3Bucket.log
import com.malliina.pics.BucketName
import com.malliina.util.AppLogger
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, CreateBucketResponse, HeadBucketRequest}

object S3Bucket:
  private val log = AppLogger(getClass)

class S3Bucket[F[_]: Async](client: S3AsyncClient):
  def createIfNotExists(bucket: BucketName): F[Boolean] =
    exists(bucket).flatMap: doesExist =>
      if doesExist then Sync[F].pure(true)
      else create(bucket).map(_ => true)

  def create(bucket: BucketName): F[CreateBucketResponse] =
    client.createBucket(CreateBucketRequest.builder().bucket(bucket.name).build()).io[F]

  def exists(bucket: BucketName): F[Boolean] =
    client
      .headBucket(HeadBucketRequest.builder().bucket(bucket.name).build())
      .io[F]
      .map(_ => true)
      .handleErrorWith: e =>
        log.warn(s"Unable to make HEAD request to bucket '$bucket'.", e)
        Sync[F].pure(false)
