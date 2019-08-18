package org.stella.ai.tv

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalTime}
import java.util.Locale

import akka.actor.{Actor, Props, Status}
import org.stella.ai.tv.TvProgramCollector.ProgramsFound
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

  val URLDayFormatter = DateTimeFormatter.ofPattern("EEEE-dd-MMMM-yyyy", Locale.FRENCH)
  val ProgramTimeFormatter = DateTimeFormatter.ofPattern("HH'h'mm")
  val ProgramsPattern = "(?s)<div class=\"grid_col bu_tvprogram_logo\">.*?<div>.*?([0-9h]+).*?</div>.+?<span class=\"bu_tvprogram_typo2\">(.+?)</span>.+?<span class=\"bu_tvprogram_typo5\">(.+?)</span>".r

  implicit val ec = context.dispatcher

  WebClient.get(buildURL(date, channel)) onComplete {
    case Success(body) => self ! body
    case Failure(err) => self ! Status.Failure(err)
  }

  def buildURL(date: LocalDate, channel: String): String = {
    "https://www.linternaute.com/television/programme-" + channel + "-"+date.format(URLDayFormatter)+"/"
  }

  def parseBody(body: String) : List[TvProgram] = {
    ProgramsPattern.findAllMatchIn(body).map(m => TvProgram(LocalTime.parse(m.group(1),ProgramTimeFormatter), channel, HtmlEscape.unescapeHtml(m.group(2).trim()), HtmlEscape.unescapeHtml(m.group(3).trim()))).toList
  }

  override def receive = {
    case body: String => context.parent ! ProgramsFound((date, parseBody(body)))
  }
}
