package org.stella.brain.programs

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalTime}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.unbescape.html.HtmlEscape

import scala.io.Source


/**
  * Calls the linternaute.com website to retrieve a webpage with all the programs for the specified channel on the specified date,
  * parses the body and returns a list of tuples.
  *
  */
object LInternauteOverviewCrawler {

  private val DAYS_OF_WEEK = Array("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
  private val MONTHS_OF_YEAR = Array("janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre", "octobre", "novembre", "decembre")
  private val ProgramTimeFormatter = DateTimeFormatter.ofPattern("HH'h'mm")
  private val ProgramsPattern = "(?s)<div class=\"grid_col bu_tvprogram_logo\">.*?<div>.*?([0-9h]+).*?</div>.+?<span class=\"bu_tvprogram_typo2\">(.+?)</span>.+?<span class=\"bu_tvprogram_typo5\">(.+?)</span>".r

   /**
    * PROTOCOL
    *
    */
  final case class LInternauteOverviewRequest(date: LocalDate, channel: String, replyTo: ActorRef[LInternauteOverviewTvPrograms])
  final case class LInternauteOverviewTvPrograms(date: LocalDate, channel: String, programs: List[Program])

  def apply() =  Behaviors.receive[LInternauteOverviewRequest] { (_, message) =>
    message.replyTo ! LInternauteOverviewTvPrograms(message.date, message.channel, parseBody(message.date, message.channel, readPage(buildURL(message.date, message.channel))))
    Behaviors.stopped
  }

  def readPage(url: String): String = {
    import java.net.{HttpURLConnection, URL}
    val connection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(5000)
    connection.setReadTimeout(2000)
    connection.setRequestMethod("GET")
    val inputStream = connection.getInputStream
    val content = Source.fromInputStream(inputStream).mkString
    if (inputStream != null) inputStream.close()
    content
  }

  private def buildURL(date: LocalDate, channel: String): String = {
    val formattedDate = buildDate(date)
    s"https://www.linternaute.com/television/programme-$channel-$formattedDate/"    
  }

  // DateTimeFormatter.ofPattern("EEEE-dd-MMMM-yyyy", Locale.FRENCH) ALMOST does the trick but it generates some months with an accent e.g. août when linternaute expects aout
  private def buildDate(date: LocalDate): String = {
    val dayOfWeek = DAYS_OF_WEEK(date.getDayOfWeek.ordinal())
    val dayOfMonth = date.getDayOfMonth
    val monthOfYear = MONTHS_OF_YEAR(date.getMonthValue - 1)
    val year = date.getYear
    s"$dayOfWeek-$dayOfMonth-$monthOfYear-$year"
  }

  private def parseBody(date: LocalDate, channel: String, body: String) : List[Program] = {
    ProgramsPattern.findAllMatchIn(body).map(m => Program(LocalTime.parse(m.group(1),ProgramTimeFormatter).atDate(date), channel, HtmlEscape.unescapeHtml(m.group(2).trim()), HtmlEscape.unescapeHtml(m.group(2).trim()))).filter(_.summary != "").toList
  }
}
