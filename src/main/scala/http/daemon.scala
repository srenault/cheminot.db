package m.cheminot.http

import rapture.core._
import rapture.json._, jsonBackends.jawn._
import rapture.uri._
import rapture.http._, httpBackends.jetty._, requestExtractors._
import rapture.codec._
import encodings.`UTF-8`._
import scala.concurrent.stm._
import m.cheminot.Config
import m.cheminot.build.DB

object Daemon {

  val LIMIT = 20

  def start(db: DB)(implicit config: Config): Unit = {

    println(s"Starting http server...")

    State.set(db)

    HttpServer.listen(config.port) {

      case req@Path(^ / "api" / "db" / "build") if isPostReq(req) =>

        Json.parse(req.body).verticeIds.as[Option[List[String]]] match {

          case Some(verticeIds) if verticeIds.size <= LIMIT && !verticeIds.isEmpty =>
            val embed = State.subset(verticeIds)
            val file = DB.buildEmbed(embed)
            json"""{ "uri": ${file.javaFile.getAbsolutePath} }"""

          case Some(verticeIds) =>
            BadRequest(s"You can only ask up to ${LIMIT} vertices}")

          case _ =>
            BadRequest("Please specify verticeIds")
        }

      case _ => Response.NotFound
    }

    println(s"Listening on port ${config.port}")
  }
}

object State {

  private var db = Ref(DB.empty)

  def set(newdb: DB): DB = atomic { implicit txn =>
    db() = newdb
    newdb
  }

  def subset(verticeIds: Seq[String]): DB = {
    val current = db.single()
    DB.subset(current, verticeIds)
  }
}
