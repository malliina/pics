package com.malliina.pics

import java.awt.image.BufferedImage
import java.awt.{Color, RenderingHints}
import java.io.{IOException, InputStream}
import java.nio.file.Path
import javax.imageio.ImageIO

import org.apache.commons.io.FilenameUtils

object Resizer {
  val Prod = Resizer(maxWidth = 400, maxHeight = 300)

  def apply(maxWidth: Int, maxHeight: Int): Resizer = new Resizer(maxWidth, maxHeight)
}

/**
  * @param maxWidth  max width of resized image
  * @param maxHeight max height of resized image
  */
class Resizer(maxWidth: Int, maxHeight: Int) {
  def resizeFromFile(src: Path, dest: Path): Either[ImageFailure, BufferedImage] = {
    val format = FilenameUtils.getExtension(src.getFileName.toString)
    try {
      val resized = resize(ImageIO.read(src.toFile))
      val writerFound = ImageIO.write(resized, format, dest.toFile)
      if (writerFound) Right(resized)
      else Left(UnsupportedFormat(format, ImageIO.getWriterFormatNames().toList.map(_.toLowerCase).distinct))
    } catch {
      case ioe: IOException => Left(ImageException(ioe))
    }
  }

  def resizeFromStream(src: InputStream): BufferedImage =
    resize(ImageIO.read(src))

  /**
    * @param src original image
    * @return a resized image
    * @see https://docs.aws.amazon.com/lambda/latest/dg/with-s3-example-deployment-pkg.html
    */
  def resize(src: BufferedImage): BufferedImage = {
    val srcHeight = src.getHeight()
    val srcWidth = src.getWidth()
    val scalingFactor = Math.min(1.0d * maxWidth / srcWidth, 1.0d * maxHeight / srcHeight)
    val width = (scalingFactor * srcWidth).toInt
    val height = (scalingFactor * srcHeight).toInt
    val resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = resized.createGraphics()
    g.setPaint(Color.white)
    g.fillRect(0, 0, width, height)
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.drawImage(src, 0, 0, width, height, null)
    g.dispose()
    resized
  }
}
