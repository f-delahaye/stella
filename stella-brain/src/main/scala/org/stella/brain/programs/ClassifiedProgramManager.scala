package org.stella.brain.programs

import java.time.LocalDate

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

/**
 * Keeps track of classified programs within the system.
 * There should be one instance of ClassifiedProgramManager per user supported by Stella.
 *
 * Programs are collected then classified and finally are sent to the ClassifiedProgramManager where they may be requested by users.
 * Only a few days worth's of classified programs are actually stored internally.
 */
object ClassifiedProgramManager {

  type Classified = (Program, Double) // program, score

  sealed trait ClassifiedProgramManagerMessage

  final case class ClassifiedProgramsNotification(classifiedPrograms: List[Classified]) extends ClassifiedProgramManagerMessage

  // Client issuing a request to get all classified data that this manager knows on the specified date
  final case class ClassifiedProgramsRequest(date: LocalDate, replyTo: ActorRef[ClassifiedPrograms]) extends ClassifiedProgramManagerMessage

  final case class ClassifiedPrograms(classified: List[Classified])

  def apply(): Behavior[ClassifiedProgramManagerMessage] =
    handle(Map.empty)

  private def handle(classified: Map[LocalDate, List[Classified]]): Behavior[ClassifiedProgramManagerMessage] =
      Behaviors.receiveMessage {
        case ClassifiedProgramsNotification(newClassified) =>
          val newClassifiedMap: Map[LocalDate, List[Classified]] = newClassified.groupBy(_._1.time.toLocalDate)
          // WATCH OUT!
          // Map.++ overrides any existing value so if a given local date exists both in classified and newClassified,
          // the latter will override any programs from the former.
          // TODO the resulting map should be bound to keep only a few days worth of data
          handle(classified++newClassifiedMap)
        case ClassifiedProgramsRequest(date, replyTo) =>
          replyTo ! ClassifiedPrograms(classified(date))
          Behaviors.same
    }
}
