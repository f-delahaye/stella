package org.stella.brain.programs

import java.io.InputStream
import java.net.{HttpURLConnection, URL}
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalTime}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.stella.brain.app.StellaConfig
import org.stella.brain.programs.LInternauteOverviewCrawler.RequestMode.RequestMode
import org.stella.brain.programs.ProgramCache.ProgramsLoadedFromCache
import org.unbescape.html.HtmlEscape

import scala.io.Source


/**
  * Calls the linternaute.com website to retrieve a webpage with all the programs for the specified channel on the specified date,
  * parses the body and returns a list of tuples.
  *
  */
object LInternauteOverviewCrawler {

  type URLStreamProvider = (LocalDate, String) => InputStream

  object RequestMode extends Enumeration {
    type RequestMode = Value
    val CacheOrUrl, Url, TestFile = Value
  }

  private val DAYS_OF_WEEK = Array("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
  private val MONTHS_OF_YEAR = Array("janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre", "octobre", "novembre", "decembre")
  private val ProgramTimeFormatter = DateTimeFormatter.ofPattern("HH'h'mm")
  private val ProgramsPattern = "(?s)<div class=\"grid_col bu_tvprogram_logo\">.*?<div>.*?([0-9h]+).*?</div>.+?<span class=\"bu_tvprogram_typo2\">(.+?)</span>.+?<span class=\"bu_tvprogram_typo5\">(.+?)</span>".r

   /**
    * PROTOCOL
    *
    */
  sealed trait Command
  // request from user
  final case class RequestLInternautePrograms(date: LocalDate, channel: String,replyTo: ActorRef[SendLInternautePrograms], mode: RequestMode = StellaConfig.getLInternauteRequestMode) extends Command
  // internal message emitted when the programs have been loaded from the cache.
  final private[programs] case class ProgramsLoadedFromCacheAdapted(date: LocalDate, channel: String, programs: Option[List[Program]], replyTo: ActorRef[SendLInternautePrograms]) extends Command
  // response back to user
  final case class SendLInternautePrograms(date: LocalDate, channel: String, programs: List[Program])

  def apply(cache: ActorRef[ProgramCache.Command], urlStreamProvider: URLStreamProvider = buildHttpURLConnection): Behavior[Command] =
    Behaviors.setup { context =>

      // load programs, from a location which depends on the specified mode.
      // For non cache based modes, programs will be returned to replyTo.
      // For cache based modes, a message will be sent to the cache, and the programs will be sent back to client upon receiving the cache's answer.
      // In case of cache miss, the default url based mode will be used instead
      def loadPrograms(date: LocalDate, channel: String, mode: RequestMode, storePrograms: Boolean, replyTo: ActorRef[SendLInternautePrograms]): Unit = {

        def loadNonCachedPrograms(content: String) = {
          val programs = parseBody(date, channel, content)
          if (storePrograms) {
            cache ! ProgramCache.StoreProgramsInCache(date, channel, programs)
          }
          replyTo ! SendLInternautePrograms(date, channel, programs)
        }

        mode match {
          case RequestMode.Url =>
            loadNonCachedPrograms(Source.fromInputStream(urlStreamProvider(date, channel)).mkString)
          case RequestMode.TestFile =>
            loadNonCachedPrograms(Source.fromResource(s"/programmes/$channel-2020-01-19.html").mkString)
          case RequestMode.CacheOrUrl =>
            val adapter = context.messageAdapter[ProgramsLoadedFromCache](resp => ProgramsLoadedFromCacheAdapted(date, channel, resp.programs, replyTo))
            cache ! ProgramCache.LoadProgramsFromCache(date, channel, adapter)
        }
      }

      Behaviors.receiveMessage {
        case RequestLInternautePrograms(date, channel, replyTo, mode) =>
          loadPrograms(date, channel, mode, storePrograms = false, replyTo)
          Behaviors.same
        case ProgramsLoadedFromCacheAdapted(date, channel, programs, replyTo) =>
          if (programs.nonEmpty) {
            replyTo ! SendLInternautePrograms(date, channel, programs.get)
          } else {
            loadPrograms(date, channel, RequestMode.Url, storePrograms = true, replyTo)
          }
          Behaviors.same
      }
  }

  private[programs] def buildHttpURLConnection(date: LocalDate, channel: String): InputStream = {
    val formattedDate = buildDate(date)
    val url = s"https://www.linternaute.com/television/programme-$channel-$formattedDate/"
    val connection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(5000)
    connection.setReadTimeout(2000)
    connection.setRequestMethod("GET")
    connection.getInputStream
  }


  // DateTimeFormatter.ofPattern("EEEE-dd-MMMM-yyyy", Locale.FRENCH) ALMOST does the trick but it generates some months with an accent e.g. août when linternaute expects aout
  private def buildDate(date: LocalDate): String = {
    val dayOfWeek = DAYS_OF_WEEK(date.getDayOfWeek.ordinal())
    val dayOfMonth = date.getDayOfMonth
    val monthOfYear = MONTHS_OF_YEAR(date.getMonthValue - 1)
    val year = date.getYear
    s"$dayOfWeek-$dayOfMonth-$monthOfYear-$year"
  }

  private def parseBody(date: LocalDate, channel: String, body: String) : List[Program] =
    ProgramsPattern.findAllMatchIn(body).map(m => Program(LocalTime.parse(m.group(1),ProgramTimeFormatter).atDate(date), channel, HtmlEscape.unescapeHtml(m.group(2).trim()), HtmlEscape.unescapeHtml(m.group(2).trim()))).filter(_.summary != "").toList
}
