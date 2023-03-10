import org.apache.ivy.util.ChecksumHelper
import sbt.Keys.{streams, target}
import sbt._
import sbt.internal.util.ManagedLogger
import com.malliina.sitegen.FileIO
import com.malliina.storage.StorageLong

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

case class HashedFile(path: String, hashedPath: String, originalFile: Path, hashedFile: Path) {
  def size = Files.size(originalFile).bytes
}

object HashedFile {
  def from(original: Path, hashed: Path, root: Path) = HashedFile(
    root.relativize(original).toString.replace('\\', '/'),
    root.relativize(hashed).toString.replace('\\', '/'),
    original,
    hashed
  )
}

object HashPlugin extends AutoPlugin {
  val algorithm = "md5"

  object autoImport {
    val hashIncludeExts = settingKey[Seq[String]]("Extensions to hash")
    val hashRoot = settingKey[Path]("Root dir")
    val hashAssets = taskKey[Seq[HashedFile]]("Hashed files")
    val hashPackage = settingKey[String]("Package name for assets file")
    val hash = taskKey[Seq[Path]]("Create hash")
    val copyFolders = settingKey[Seq[Path]]("Copy folders")
    val copy = taskKey[Seq[Path]]("Copies folders")
  }
  import autoImport._
  override val projectSettings: Seq[Def.Setting[?]] = Seq(
    copyFolders := Nil,
    copy := {
      val log = streams.value.log
      val root = hashRoot.value
      copyFolders.value.flatMap { dir =>
        val dirPath = dir
        allPaths(dirPath).flatMap { path =>
          val rel = dirPath.relativize(path)
          val dest = root.resolve(rel)
          if (Files.isRegularFile(path)) {
            FileIO.copyIfChanged(path, dest)
            Option(dest)
          } else if (Files.isDirectory(path)) { Option(Files.createDirectories(dest)) }
          else None
        }
      }
    },
    hashIncludeExts := Seq(".css", ".js", ".jpg", ".jpeg", ".png", ".svg", ".ico"),
    hashPackage := "com.malliina.sitegen",
    hashAssets := {
      val log = streams.value.log
      val root = hashRoot.value
      val exts = hashIncludeExts.value
      allPaths(root).filter { p =>
        val name = p.getFileName.toString
        Files.isRegularFile(p) &&
        exts.exists(ext => name.endsWith(ext)) &&
        name.count(c => c == '.') < 2
      }.map { file =>
        HashedFile.from(file, prepFile(file, log), root)
      }
    },
    hashAssets := hashAssets
      .dependsOn(copy, Def.task(Files.createDirectories(hashRoot.value)))
      .value,
    hash := {
      val hashes = hashAssets.value
      val log = streams.value.log
      val cached = FileFunction.cached(streams.value.cacheDirectory / "assets") { in =>
        val file = makeAssetsFile(
          target.value,
          hashPackage.value,
          "assets",
          hashes,
          log
        )
        Set(file)
      }
      cached(hashes.map(_.hashedFile.toFile).toSet).toSeq.map(_.toPath)
    }
  )

  def prepFile(file: Path, log: Logger) = {
    val checksum = ChecksumHelper.computeAsString(file.toFile, algorithm)
    val checksumFile = file.getParent.resolve(s"${file.getFileName}.$algorithm")
    if (!Files.exists(checksumFile)) {
      Files.writeString(checksumFile, checksum)
      log.debug(s"Wrote $checksumFile.")
    }
    val (base, ext) = file.toFile.baseAndExt
    val hashedFile = file.getParent.resolve(s"$base.$checksum.$ext")
    if (!Files.exists(hashedFile)) {
      Files.copy(file, hashedFile)
      log.info(s"Wrote $hashedFile.")
    }
    hashedFile
  }

  def makeAssetsFile(
    base: File,
    packageName: String,
    prefix: String,
    hashes: Seq[HashedFile],
    log: ManagedLogger
  ): File = {
    val inlined = hashes.map(h => s""""${h.path}" -> "${h.hashedPath}"""").mkString(", ")
    val dataUris = hashes
      .filter(h => h.size < 48.kilos)
      .map { h =>
        val dataUri = FileIO.dataUri(h.originalFile)
        s""""${h.path}" -> "$dataUri""""
      }
      .mkString(", ")
    val objectName = "HashedAssets"
    val content =
      s"""
         |package $packageName
         |
         |object $objectName {
         |  val prefix: String = "$prefix"
         |  val assets: Map[String, String] = Map($inlined)
         |  val dataUris: Map[String, String] = Map($dataUris)
         |}
         |""".stripMargin.trim + IO.Newline
    val destFile = destDir(base, packageName) / s"$objectName.scala"
    IO.write(destFile, content, StandardCharsets.UTF_8)
    log.info(s"Wrote $destFile.")
    destFile
  }

  def destDir(base: File, packageName: String): File =
    packageName.split('.').foldLeft(base)((acc, part) => acc / part)

  def allPaths(root: Path) = FileIO.using(Files.walk(root))(_.iterator().asScala.toList)
}
