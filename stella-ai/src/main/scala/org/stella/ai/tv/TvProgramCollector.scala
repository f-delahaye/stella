package org.stella.ai.tv

import java.time.LocalDate

import akka.actor.{Actor, Props}
import org.stella.ai.tv.TvProgramCollector.{CollectPrograms, ProgramsFound}

object TvProgramCollector {
  case class ProgramsFound(programs: (LocalDate, List[TvProgram]))
  case class CollectPrograms(date: LocalDate)

  //  https://doc.akka.io/docs/akka/current/actors.html#recommended-practices
  def props: Props = Props(new TvProgramCollector)
}

/**
  * Collects programs on the specified date from a list of channels.
  * This actor queries a main web site (with possibly - not currently implemented - a backup web site should the default one be unavailable, or if its results can not be parsed e.g. because its layout has changed)
  * Returns a list of all programs across all listed channels on the specified date
  */
class TvProgramCollector extends Actor {
/*
  val classifiedPrograms: mutable.Map[Int, List[(LocalTime, String, String)]] = mutable.Map[Int, List[(LocalTime, String, String)]]()

  def receive = {
    case Classify(date:LocalDate) => context.actorOf(Props(new LInternauteOverviewTvProgramCrawler(date, "histoire-tps")))
    case ProgramsFound(programs: List[(LocalTime, String, String)]) => {
      classifiedPrograms ++= programs.groupBy(classificationKey)
      println(classifiedPrograms)
    }
  }

  def classificationKey(program: (LocalTime, String, String)): Int = {
    // TODO this should be ML based. For the time being, we only classify peint[re/ure] and religi[on/eux]
    if (program._3.contains("peint") || program._3.contains("religi")) {
      1;
    } else {
      2;
    }
  }
*/

  override def receive: Actor.Receive = {
    case CollectPrograms(date) =>
      context.actorOf(LInternauteOverviewTvProgramCrawler.props(date, "histoire-tps"))
    case ProgramsFound(programs) =>
      sender() ! ProgramsFound(programs) // TODO query all supported channels and aggregate them before sending back to parent
  }

}
