package com.malliina.pics

import com.malliina.pics.ImageFailure.{ImageException, ImageReaderFailure, UnsupportedFormat}

import java.awt.image.BufferedImage
import java.awt.{Color, RenderingHints}
import java.io.{IOException, InputStream}
import java.nio.file.Path
import javax.imageio.ImageIO
import org.apache.commons.io.FilenameUtils

object Resizer:
  val Small400x300 = Resizer(maxWidth = 400, maxHeight = 300)
  val Medium1440x1080 = Resizer(1440, 1080)

/** @param maxWidth
  *   max width of resized image
  * @param maxHeight
  *   max height of resized image
  */
class Resizer(maxWidth: Int, maxHeight: Int):
  def resizeFromFile(src: Path, dest: Path): Either[ImageFailure, BufferedImage] =
    val format = FilenameUtils.getExtension(src.getFileName.toString)
    try
      Option(ImageIO.read(src.toFile))
        .map: bufferedImage =>
          val resized = resize(bufferedImage)
          val writerFound = ImageIO.write(resized, format, dest.toFile)
          if writerFound then Right(resized)
          else
            Left(
              UnsupportedFormat(
                format,
                ImageIO.getWriterFormatNames.toList.map(_.toLowerCase).distinct
              )
            )
        .getOrElse:
          Left(ImageReaderFailure(src))
    catch case ioe: IOException => Left(ImageException(ioe))

  def resizeFromStream(src: InputStream): BufferedImage =
    resize(ImageIO.read(src))

  /** @param src
    *   original image
    * @return
    *   a resized image
    * @see
    *   https://docs.aws.amazon.com/lambda/latest/dg/with-s3-example-deployment-pkg.html
    */
  def resize(src: BufferedImage): BufferedImage =
    val srcHeight = src.getHeight()
    val srcWidth = src.getWidth()
    val scalingFactor = Math.min(1.0d * maxWidth / srcWidth, 1.0d * maxHeight / srcHeight)
    val width = (scalingFactor * srcWidth).toInt
    val height = (scalingFactor * srcHeight).toInt
    val resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = resized.createGraphics()
    g.setPaint(Color.white)
    g.fillRect(0, 0, width, height)
    g.setRenderingHint(
      RenderingHints.KEY_INTERPOLATION,
      RenderingHints.VALUE_INTERPOLATION_BILINEAR
    )
    g.drawImage(src, 0, 0, width, height, null)
    g.dispose()
    resized
