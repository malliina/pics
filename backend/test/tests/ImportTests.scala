package tests

import java.nio.file.{Files, Paths}
import java.text.SimpleDateFormat

import com.malliina.pics.db.{Conf, PicsDatabase}
import com.malliina.pics.{Key, PicOwner}

import scala.jdk.CollectionConverters.ListHasAsScala

class ImportTests extends BaseSuite {
  ignore("connect") {
    val conf = Conf("jdbc:mysql://todo/pics", "user", "pass", Conf.DefaultDriver)
    val db = PicsDatabase.mysql(conf)
    import db.api._
    import db.mappings._
    val lines =
      Files.readAllLines(Paths.get(sys.props("user.home")).resolve("files/pics.csv")).asScala.toList
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val triples = lines.map { line =>
      val Array(key, owner, date) = line.split(";")
      (Key(key), PicOwner(owner), sdf.parse(date).toInstant)
    }
    val op = db.picsTable.map(t => (t.key, t.owner, t.added)) ++= triples
    await(db.database.run(op))
    val pics = await(db.database.run(db.picsTable.result))
    assert(pics.nonEmpty)
  }
}
