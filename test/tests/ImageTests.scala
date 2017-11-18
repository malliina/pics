package tests

import java.nio.file.{Files, Paths}
import javax.imageio.ImageIO

import com.malliina.pics.Resizer
import org.scalatest.FunSuite

class ImageTests extends FunSuite {
  test("image formats") {
    val atLeastSupported = Seq("jpg", "jpeg", "png", "gif")
    val actual = ImageIO.getWriterFormatNames().toSeq
    assert(atLeastSupported.forall(mustExist => actual.contains(mustExist)))
  }

  test("resize an image") {
    val resizer = Resizer(400, 300)
    val original = Paths.get("files/original.jpg")
    assert(Files.exists(original))
    val dest = Paths.get("files/resized.jpg")
    val outcome = resizer.resizeFromFile(original, dest)
    assert(outcome.isRight)
  }
}
