package tests

import cats.effect.IO
import com.malliina.pics.{ContentType, ImageException, Resizer, ScrimageResizer}
import com.malliina.storage.{StorageInt, StorageSize}

import java.nio.file.{Files, Paths}
import javax.imageio.ImageIO

class ImageTests extends munit.CatsEffectSuite:
  private val picDir = Paths.get("backend/files")
  private val original = picDir.resolve("original.jpg")
  private val origLarge = picDir.resolve("demo-original.jpeg")

  test("file content type") {
    assert(ContentType.parse("image.jpg").contains(ContentType.ImageJpeg))
  }

  test("Files.probeContentType does not work".ignore) {
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

  test("resize to medium".ignore) {
    val orig = Paths get "original.jpeg"
    val resizer = Resizer.Medium1440x1080
    val result = resizer.resizeFromFile(orig, picDir.resolve("demo.jpeg"))
    assert(result.isRight)
  }

  test("resize to small".ignore) {
    val resizer = ScrimageResizer.Small[IO]
    resizeWith(resizer, "demo-scrimage-small.jpeg").map { result =>
      assert(result.isRight)
      val size = result.toOption.get
      assert(size < 100.kilos)
    }
  }

  test("resize to medium scrimage".ignore) {
    val resizer = ScrimageResizer.Medium[IO]
    resizeWith(resizer, "demo-scrimage-normal.jpeg").map { result =>
      assert(result.isRight)
      val size = result.toOption.get
      assert(size < 200.kilos)
    }
  }

  test("resize to large scrimage".ignore) {
    val resizer = ScrimageResizer.Large[IO]
    resizeWith(resizer, "demo-scrimage-large.jpeg").map { result =>
      assert(result.isRight)
      val size = result.toOption.get
      assert(size < 600.kilos)
    }
  }

  def resizeWith(
    resizer: ScrimageResizer[IO],
    name: String
  ): IO[Either[ImageException, StorageSize]] =
    resizer.resizeFile(origLarge, picDir.resolve(name))
