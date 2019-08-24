package org.stella.ai.tv

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalTime}
import java.util.Locale

import akka.actor.{Actor, Props, Status}
import org.stella.ai.tv.TvProgramCollector.ProgramsCollected
import org.stella.ai.utils.WebClient
import org.unbescape.html.HtmlEscape

import scala.util.{Failure, Success}


object LInternauteOverviewTvProgramCrawler {
  //  https://doc.akka.io/docs/akka/current/actors.html#recommended-practices
  def props(date: LocalDate, channel: String): Props = Props(new LInternauteOverviewTvProgramCrawler(date, channel))
}

/**
  * Calls the linternaute.com website to retrieve a webpage with all the programs for the specified channel on the specified date,
  * parses the body and returns a list of tuples.
  *
  */

private class LInternauteOverviewTvProgramCrawler(date: LocalDate, channel: String) extends Actor {

  private val DAYS_OF_WEEK = Array("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
  private val MONTHS_OF_YEAR = Array("janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre", "octobre", "novembre", "decembre")
  private val ProgramTimeFormatter = DateTimeFormatter.ofPattern("HH'h'mm")
  private val ProgramsPattern = "(?s)<div class=\"grid_col bu_tvprogram_logo\">.*?<div>.*?([0-9h]+).*?</div>.+?<span class=\"bu_tvprogram_typo2\">(.+?)</span>.+?<span class=\"bu_tvprogram_typo5\">(.+?)</span>".r

  implicit val ec = context.dispatcher

  WebClient.get(buildURL(date, channel)) onComplete {
    case Success(body) => self ! body
  case Failure(err) => self ! Status.Failure(err)
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


  private def parseBody(body: String) : List[TvProgram] = {
    ProgramsPattern.findAllMatchIn(body).map(m => TvProgram(LocalTime.parse(m.group(1),ProgramTimeFormatter), channel, HtmlEscape.unescapeHtml(m.group(2).trim()), HtmlEscape.unescapeHtml(m.group(2).trim()))).filter(_.summary != "").toList
  }

  override def receive:  Actor.Receive = {
    case body: String => context.parent ! ProgramsCollected((date, parseBody(body)))
  }
}
