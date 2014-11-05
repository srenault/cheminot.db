package m.cheminot.models

import org.joda.time.DateTime
import play.api.libs.json._

case class Calendar(
  serviceId: String,
  monday: String,
  tuesday: String,
  wednesday: String,
  thursday: String,
  friday: String,
  saturday: String,
  sunday: String,
  startDate: DateTime,
  endDate: DateTime
)

object Calendar extends FormatReader {
  def fromRow(data: List[String]): Calendar = {
    Calendar(
      data.head,
      data(1),
      data(2),
      data(3),
      data(4),
      data(5),
      data(6),
      data(7),
      asDateTime(data(8)),
      asDateTime(data(9))
    )
  }

  implicit val reader: Reads[Calendar] = Json.reads[Calendar]
  implicit val dateTimeWriter = play.api.libs.json.Writes.jodaDateWrites("dd/MM/YYYY")
  implicit val writer: Writes[Calendar] = Json.writes[Calendar]
}

case class CalendarDate(
  serviceId: String,
  date: DateTime,
  exceptionType: Int
)

object CalendarDate extends FormatReader {
  def fromRow(data: List[String]): CalendarDate = {
    CalendarDate(
      data.head,
      asDateTime(data(1)),
      data(2).toInt
    )
  }

  implicit val reader: Reads[CalendarDate] = Json.reads[CalendarDate]
  implicit val writer: Writes[CalendarDate] = Json.writes[CalendarDate]
}
