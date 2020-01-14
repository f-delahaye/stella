package org.stella.brain.programs

import java.time.LocalDate

import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.eventstream.EventStream.Publish
import akka.actor.typed.scaladsl.Behaviors
import org.stella.brain.programs.ClassifiedProgramManager.ClassifiedProgramManagerMessage
import org.stella.brain.programs.ProgramClassifier.{ClassAndScore, ProgramClassifierMessage}
import org.stella.brain.programs.UntrainedProgramManager.UntrainedProgramManagerMessage
import org.stella.brain.user.RSocketUserManager

import scala.concurrent.duration._

/**
 * The main controller. It coordinates everything but does not implement logic itself.
 *
 * - Works out which day should be collected
 * - Calls relevant actors to collect data
 * - Passes collected data to the classifier as well as to the event stream where they may be picked up by any user connection sitting there
 *
 * It creates the following actors which are expected to be singleton-like i.e. not created anywhere else:
 * - ProgramClassifier
 * - UntrainedProgramManager
 * - ClassifiedProgramManager
 * In addition, it also creates a rSocket connection to send untrained data to & receive untrained data from user.
 */
// ideally, there would be one ProgramClassifier and one UntrainedProgramManager per client, however i don't know how to have one rsocket connection per user.
object ProgramController {

  sealed trait ProgramControllerMessage
  final case object ProgramByDateTick extends ProgramControllerMessage
  final case class ProgramsByDateAdapted(date: LocalDate, programs: List[Program]) extends ProgramControllerMessage
  final case class ProgramsClassificationAdapted(classifiedPrograms: List[(Program, ClassAndScore)]) extends ProgramControllerMessage

/*
val programClassifier = context.spawn(ProgramClassifier(), "ProgramClassifier")
val untrainedProgramManager = context.spawn(UntrainedProgramManager(), "UntrainedProgramManager")
val classifiedProgramManager = context.spawn(ClassifiedProgramManager(), "ClassifiedProgramManager")
*/
  def apply(programClassifier: ActorRef[ProgramClassifierMessage], untrainedProgramManager: ActorRef[UntrainedProgramManagerMessage], classifiedProgramManager: ActorRef[ClassifiedProgramManagerMessage], eventStream: ActorRef[EventStream.Command]): Behavior[ProgramControllerMessage] = {
    Behaviors.setup { context =>
      //RSocketUserManager(untrainedProgramManager, context.system)
      Behaviors.receiveMessage {
        case ProgramByDateTick =>
          val date = LocalDate.now
          val adapter = context.messageAdapter[ProgramCollector.ProgramsByDate](response => ProgramsByDateAdapted(response.date, response.programs))
          context.spawn(ProgramCollector.forTv(), "Collector on " + date) ! ProgramCollector.ProgramsByDateRequest(date, adapter)
          Behaviors.same
        case ProgramsByDateAdapted(date, programs) =>
          val programsClassificationAdapter = context.messageAdapter[ProgramClassifier.ProgramsClassification](response => ProgramsClassificationAdapted(response.classifications))
          programClassifier ! ProgramClassifier.ProgramsClassificationRequest(programs, programsClassificationAdapter)
          // extract summaries out of programs.
          untrainedProgramManager ! UntrainedProgramManager.UntrainedProgramsNotification(programs.map(_.summary))
          eventStream ! Publish(programs)
          context.scheduleOnce(1.day, context.self, ProgramByDateTick)
          Behaviors.same
        case ProgramsClassificationAdapted(classifiedPrograms) =>
          classifiedProgramManager ! ClassifiedProgramManager.ClassifiedProgramsNotification(classifiedPrograms.map(p => (p._1, p._2._2)))
          Behaviors.same
      }
    }
  }
  /*
      Behaviors.setup { _ =>
        Behaviors.receiveMessage {
      case request: ProgramsByDateRequest =>
    }
   */


}
