package m.cheminot.build.storage

import m.cheminot.misc.CSVWriteFile
import m.cheminot.build._
import rapture.fs._
import rapture.codec._
import encodings.`UTF-8`._
import rapture.io._

object Neo4j {

  type Row = List[String]

  private def write(dir: FileUrl, name: String, headers: Row, data: List[Row]): Unit = {
    val dataFile = dir / s"${name}1.csv"
    println(s"Writing to ${dataFile}")
    CSVWriteFile(dataFile.javaFile).write(headers +: data)
  }

  def writeIndexes(outdir: FileUrl, db: DB): Unit = {
    val lines = List(
      "CREATE INDEX ON :Meta(metaid);",
      "CREATE INDEX ON :MetaSubset(metasubsetid);",
      "CREATE INDEX ON :Station(stationid);",
      "CREATE INDEX ON :Station(parentid);",
      "CREATE INDEX ON :Stop(stationid);",
      "CREATE INDEX ON :Stop(stopid);",
      "CREATE INDEX ON :Stop(parentid);",
      "CREATE INDEX ON :Calendar(serviceid);",
      "CREATE INDEX ON :Calendar(monday);",
      "CREATE INDEX ON :Calendar(tuesday);",
      "CREATE INDEX ON :Calendar(wednesday);",
      "CREATE INDEX ON :Calendar(thursday);",
      "CREATE INDEX ON :Calendar(friday);",
      "CREATE INDEX ON :Calendar(saturday);",
      "CREATE INDEX ON :Calendar(sunday);",
      "CREATE INDEX ON :Calendar(startdate);",
      "CREATE INDEX ON :Calendar(enddate);",
      "CREATE INDEX ON :CalendarDate(date);",
      "CREATE INDEX ON :Trip(serviceid);"
    )
    val file = outdir / "index.cypher"
    lines.foreach (line => s"\n$line" >> outdir / "index.cypher")
  }

  object Nodes {

    def writeMeta(outdir: FileUrl, db: DB): Unit = {
      val headers = List("metaid:ID(Meta)", "bundledate:int", ":LABEL")
      val data = List(List(db.bundle.id.value, formatDateTime(db.bundle.id.date), "Meta"))
      write(outdir, name = "meta", headers = headers, data = data)
    }

    def writeMetaSubsets(outdir: FileUrl, db: DB): Unit = {
      val headers = List("metasubsetid:ID(MetaSubset)", "updateddate:int", "startdate:int", "enddate:int", ":LABEL")
      val data = db.bundle.data.subsetDirs.map { subsetDir =>
        List(
          subsetDir.id,
          subsetDir.updatedDate map(formatDate) getOrElse "",
          subsetDir.startDate map(formatDate) getOrElse "",
          subsetDir.endDate map(formatDate) getOrElse "",
          "MetaSubset"
        )
      }
      write(outdir, name = "metasubsets", headers = headers, data = data)
    }

    def writeStations(outdir: FileUrl, db: DB): Unit = {
      val headers = List("stationid:ID(Station)", "name:string", "parentid:string", "lat:double", "lng:double", ":LABEL")
      val data = db.graph.values.toList.map { stop =>
        val parentId = if(Stop.isParis(stop.id)) Stop.STOP_PARIS else ""
        List(stop.id, stop.name, parentId, stop.lat.toString, stop.lng.toString, "Station")
      }
      write(outdir, name = "stations", headers = headers, data = data)
    }

    def writeStops(outdir: FileUrl, db: DB): Unit = {
      val headers = List("stopid:ID(Stop)", "stationid:string", "parentid:string", ":LABEL")
      val data = db.trips.values.toList.flatMap { trip =>
        trip.stopTimes.map { stopTime =>
          val parentId = if(Stop.isParis(stopTime.stopId)) Stop.STOP_PARIS else ""
          List(stopTime.id, stopTime.stopId, parentId, "Stop")
        }
      }
      write(outdir, name = "stops", headers = headers, data = data)
    }

    def writeTrips(outdir: FileUrl, db: DB): Unit = {
      val headers = List("tripid:ID(Trip)", "serviceid:string", ":LABEL")
      val data = db.trips.values.toList.map { trip =>
        List(trip.id, trip.calendar.map(_.serviceId).getOrElse(""), "Trip")
      }
      write(outdir, name = "trips", headers = headers, data = data)
    }

    def writeCalendar(outdir: FileUrl, db: DB): Unit = {
      val headers = List(
        "serviceid:ID(Calendar)",
        "monday:boolean",
        "tuesday:boolean",
        "wednesday:boolean",
        "thursday:boolean",
        "friday:boolean",
        "saturday:boolean",
        "sunday:boolean",
        "startdate:int",
        "enddate:int",
        ":LABEL"
      )

      val data = db.calendar.map { calendar =>
        List(
          calendar.serviceId,
          calendar.monday.toString,
          calendar.tuesday.toString,
          calendar.wednesday.toString,
          calendar.thursday.toString,
          calendar.friday.toString,
          calendar.saturday.toString,
          calendar.sunday.toString,
          formatDate(calendar.startDate),
          formatDate(calendar.endDate),
          "Calendar"
        )
      }

      write(outdir, name = "calendar", headers = headers, data = data)
    }

    def writeCalendarDates(outdir: FileUrl, db: DB): Unit = {
      val headers = List("calendardateid:ID(CalendarDate)", "serviceid:string", "date:int", ":LABEL")
      val data = db.calendarDates.map { calendarDate =>
        List(calendarDate.id, calendarDate.serviceId, formatDate(calendarDate.date), "CalendarDate")
      }
      write(outdir, name = "calendardates", headers = headers, data = data)
    }
  }

  object Relationships {

    def writeStop2Station(outdir: FileUrl, db: DB): Unit = {
      val headers = List(":START_ID(Stop)", ":END_ID(Station)", ":TYPE")
      val data = db.trips.values.toList.flatMap { trip =>
        trip.stopTimes.flatMap { stopTime =>
          db.trips.get(stopTime.tripId).orElse {
            println(">> Not a valid trip " + stopTime.tripId)
            None
          }.map { trip =>
            List(stopTime.id, stopTime.stopId, "AT")
          }
        }
      }
      write(outdir, name = "stop2station", headers = headers, data = data)
    }

    def writeTrip2Stop(outdir: FileUrl, db: DB): Unit = {
      val headers = List(":START_ID(Trip)", ":END_ID(Stop)", "arrival:int", ":TYPE")
      val data = db.trips.values.toList.flatMap { trip =>
        trip.stopTimes.headOption.toList.map { stopTime =>
          List(trip.id, stopTime.id, formatTime(stopTime.arrival), "GOES_TO")
        }
      }
      write(outdir, name = "trip2stop", headers = headers, data = data)
    }

    def writeTrip2Calendar(outdir: FileUrl, db: DB): Unit = {
      val headers = List(":START_ID(Trip)", ":END_ID(Calendar)", ":TYPE")
      val data = db.trips.values.toList.flatMap { trip =>
        trip.calendar.map { calendar =>
          List(trip.id, calendar.serviceId, "SCHEDULED_AT")
        }
      }
      write(outdir, name = "trip2calendar", headers = headers, data = data)
    }

    def writeCalendar2CalendarDates(outdir: FileUrl, db: DB): Unit = {
      val headers = List(":START_ID(Calendar)", ":END_ID(CalendarDate)", ":TYPE")
      val data = db.calendarDates.map { calendarDate =>
        val `type` = if(calendarDate.exceptionType == 1) "ON" else "OFF"
        List(calendarDate.serviceId, calendarDate.id, `type`)
      }
      write(outdir, name = "calendar2calendardates", headers = headers, data = data)
    }

    def writeStop2Stop(outdir: FileUrl, db: DB): Unit = {
      val headers = List(":START_ID(Stop)", "departure:int", "arrival:int", ":END_ID(Stop)", ":TYPE")
      val data = db.trips.values.toList.flatMap { trip =>
        trip.stopTimes.flatMap { stopTime =>
          for {
            trip <- db.trips.get(stopTime.tripId).orElse {
              println(">> Not a valid trip " + stopTime.tripId)
              None
            }
            next <- trip.stopTimes.lift(stopTime.pos + 1)
            serviceId <- trip.calendar.map(_.serviceId)
          } yield {
            val departure = formatTime(stopTime.departure)
            val arrival = formatTime(next.arrival)
            List(stopTime.id, departure, arrival, next.id, "GOES_TO")
          }
        }
      }
      write(outdir, name = "stop2stop", headers = headers, data = data)
    }

    def writeMeta2MetaSubsets(outdir: FileUrl, db: DB): Unit = {
      val headers = List(":START_ID(Meta)", ":END_ID(MetaSubset)", ":TYPE")
      val data = db.bundle.data.subsetDirs.map { subsetDir =>
        List(db.bundle.id.value, subsetDir.id, "HAS")
      }
      write(outdir, name = "meta2metasubsets", headers = headers, data = data)
    }
  }

  def write(dbDir: FileUrl, db: DB): Unit = {
    val outdir = dbDir / db.bundle.id.value / db.id
    outdir.mkdir(makeParents = true)
    Nodes.writeStations(outdir, db);
    Nodes.writeTrips(outdir, db)
    Nodes.writeStops(outdir, db)
    Nodes.writeCalendar(outdir, db)
    Nodes.writeCalendarDates(outdir, db)
    Nodes.writeMeta(outdir, db)
    Nodes.writeMetaSubsets(outdir, db)
    Relationships.writeMeta2MetaSubsets(outdir, db)
    Relationships.writeStop2Station(outdir, db)
    Relationships.writeStop2Stop(outdir, db)
    Relationships.writeTrip2Stop(outdir, db)
    Relationships.writeTrip2Calendar(outdir, db)
    Relationships.writeCalendar2CalendarDates(outdir, db)
    writeIndexes(outdir, db);
  }
}
