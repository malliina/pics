package com.malliina.pics

import com.malliina.http.FullUrl
import com.malliina.pics.AssetsSource.prefix
import com.malliina.pics.assets.HashedAssets
import com.malliina.pics.http4s.Urls
import org.http4s.Uri
import org.http4s.implicits.uri

trait AssetsSource:
  def at(file: String): Uri

object AssetsSource:
  val prefix = uri"/assets"

  def apply(isProd: Boolean): AssetsSource =
    if isProd then HashedAssetsSource
    else DirectAssets

object DirectAssets extends AssetsSource:
  override def at(file: String): Uri =
    prefix.addPath(file)

object HashedAssetsSource extends AssetsSource:
  override def at(file: String): Uri =
    val optimal = HashedAssets.assets.getOrElse(file, file)
    prefix.addPath(optimal)

class CDNAssets(cdnBaseUrl: FullUrl) extends AssetsSource:
  override def at(file: String): Uri =
    val optimal = HashedAssets.assets.getOrElse(file, file)
    val url = cdnBaseUrl / "assets" / optimal
    Urls.toUri(url)
