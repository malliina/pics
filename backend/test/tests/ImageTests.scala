package tests

import java.nio.file.{Files, Paths}

import com.malliina.pics.{ContentType, Resizer, ScrimageResizer}
import com.malliina.storage.StorageInt
import javax.imageio.ImageIO
import org.scalatest.FunSuite

class ImageTests extends FunSuite {
  val picDir = Paths.get("files")
  val original = picDir.resolve("original.jpg")

  test("file content type") {
    assert(ContentType.parse("image.jpg").contains(ContentType.ImageJpeg))
  }

  ignore("Files.probeContentType does not work") {
    // actually this might work, depending on the environment, but not on MacOS
    assert(Option(Files.probeContentType(original)).isEmpty)
  }

  test("image formats") {
    val atLeastSupported = Seq("jpg", "jpeg", "png", "gif")
    val actual = ImageIO.getWriterFormatNames.toSeq
    assert(atLeastSupported.forall(mustExist => actual.contains(mustExist)))
  }

  test("resize an image") {
    val resizer = Resizer(400, 300)
    assert(Files.exists(original))
    val dest = picDir.resolve("resized.jpg")
    val outcome = resizer.resizeFromFile(original, dest)
    assert(outcome.isRight)
  }

  ignore("resize to medium") {
    val orig = Paths get "original.jpeg"
    val resizer = Resizer.Medium1440x1080
    val result = resizer.resizeFromFile(orig, picDir.resolve("demo.jpeg"))
    assert(result.isRight)
  }

  val origLarge = picDir.resolve("demo-original.jpeg")

  ignore("resize to small") {
    val resizer = ScrimageResizer.Small
    val result = resizeWith(resizer, "demo-scrimage-small.jpeg")
    assert(result.isRight)
    val size = result.toOption.get
    assert(size < 100.kilos)
  }

  ignore("resize to medium scrimage") {
    val resizer = ScrimageResizer.Medium
    val result = resizeWith(resizer, "demo-scrimage-normal.jpeg")
    assert(result.isRight)
    val size = result.toOption.get
    assert(size < 200.kilos)
  }

  ignore("resize to large scrimage") {
    val resizer = ScrimageResizer.Large
    val result = resizeWith(resizer, "demo-scrimage-large.jpeg")
    assert(result.isRight)
    val size = result.toOption.get
    assert(size < 600.kilos)
  }

  def resizeWith(resizer: ScrimageResizer, name: String) =
    await(resizer.resizeFile(origLarge, picDir.resolve(name)))
}
