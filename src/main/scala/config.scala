package m.cheminot

import rapture.core._
import rapture.cli._
import rapture.fs._

case class Config(dbDir: FsUrl, gtfsDir: FsUrl, daemon: Boolean, port: Int)

object Config {

  implicit val fileExtractor: New.Param.Extractor[FsUrl] = new New.Param.Extractor[FsUrl] {
    def extract(values: Vector[String]): Option[FsUrl] = values match {
      case Vector(v) =>
        scala.util.Try(File.parse(v)).toOption.filter(_.exists)
      case _ => None
    }
  }

  implicit val booleanExtractor: New.Param.Extractor[Boolean] = new New.Param.Extractor[Boolean] {
    def extract(values: Vector[String]): Option[Boolean] = values match {
      case _:Vector[String] => Option(true)
      case _ => None
    }
  }

  import modes.returnOption._

  val DbDir = New.Param[FsUrl]('o', 'out)
  val GtfsDir = New.Param[FsUrl]('g', 'gtfs)
  val Daemon = New.Param[Boolean]('d', 'daemon)
  val Port = New.Param[Int]('p', 'port)

  def apply(args: Array[String]): Config = {
    val params = New.ParamMap(args:_*)
    Config(
      dbDir = DbDir.parse(params) getOrElse build.DB.defaultDbDir,
      gtfsDir = GtfsDir.parse(params) getOrElse build.GtfsBundle.defaultRoot,
      daemon = Daemon.parse(params) getOrElse false,
      port = Port.parse(params) getOrElse 8080
    )
  }
}
