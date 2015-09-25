package m.cheminot.misc

import java.io.File
import org.apache.commons.io.FileUtils
import scala.language.postfixOps
import scala.util.parsing.combinator._

object CSV extends RegexParsers {

  import CSVFile._

  override protected val whiteSpace = """[\t]""".r

  def COMMA   = ","
  def DQUOTE  = "\""
  def DQUOTE2 = "\"\"" ^^ { case _ => "\"" }
  def CR      = "\r"
  def LF      = "\n"
  def CRLF    = "\r\n"
  def TXT     = "[^\",\r\n]".r

  def file[A](collect: CollectFunct[A]): Parser[List[A]] = (repsep(record(collect), CRLF|CR|LF) <~ opt(CRLF)) ^^ (_.flatten)
  def record[A](collect: CollectFunct[A]): Parser[Option[A]] = rep1sep(field, COMMA) ^^ {
    case record =>
      scala.util.Try { collect(record) }.toOption
  }
  def field: Parser[String] = (escaped|nonescaped)
  def escaped: Parser[String] = (DQUOTE~>((TXT|COMMA|CR|LF|DQUOTE2)*)<~DQUOTE) ^^ { case ls => ls.mkString("")}
  def nonescaped: Parser[String] = (TXT*) ^^ { case ls => ls.mkString("") }

  def parse[A](s: String, collect: CollectFunct[A]): List[A] =
    parseAll(file(collect), s) match {
      case Success(res, _) => res
      case _ => Nil
    }
}

object CSVFile {
  type Record[A] = List[A]
  type CSVRecord = Record[String]
  type CollectFunct[A] = CSVRecord => A
}

case class CSVFile(file: File) {

  lazy val content = FileUtils.readFileToString(file, "utf-8")

  def read[A](collect: CSVFile.CollectFunct[A]): List[A] = {
    CSV.parse(content, collect)
  }
}
