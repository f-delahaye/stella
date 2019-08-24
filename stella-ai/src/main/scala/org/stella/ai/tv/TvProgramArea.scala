package org.stella.ai.tv

import java.time.LocalDate

import akka.actor.Actor
import org.stella.ai.tv.TvProgramArea.CheckDates
import org.stella.ai.tv.TvProgramCollector.{CollectPrograms, ProgramsCollected}

import scala.collection.mutable

object TvProgramArea {
  case class CheckDates()
}

/**
  * Asks TvProgramCollectors to collect programs on certain dates as needed,
  * When new programs are collected, stores them and sends them to classifier.
  *
  * This is the higher level actor of the TvProgram feature and it is expected to be running as soon as Stella starts.
  * If may not be working all the time but it should check on a regular basis whether or not new dates need to be handled.
  * This requires some non trivial logic, taking busy days and days off into account as well as ignoring dates already in the cache
  *
  */
class TvProgramArea() extends Actor {

  private val tvProgramCache: mutable.Map[LocalDate, List[TvProgram]] = mutable.Map[LocalDate, List[TvProgram]]()

  //implicit val ec = context.dispatcher
  //context.system.scheduler.schedule(0 seconds, 1 minutes, self, CheckDates())

  override def receive:  Actor.Receive = {
    case CheckDates() => checkDate(LocalDate.now())
    case ProgramsCollected(programs) =>
      tvProgramCache(programs._1) = programs._2
//    case LoadTvProgramClassifier.ClassifierLoaded(classifier, cdc) => this.classifier = Some(classifier);
  }

  def checkDate(date: LocalDate): Unit = {
      if (!tvProgramCache.contains(date)) {
        context.actorOf(TvProgramCollector.props) ! CollectPrograms(date)
      } else {
        println("TvPrograms on " + date + " already collected")
      }
  }
}
