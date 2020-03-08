package org.stella.brain.programs

import java.time.LocalDate

import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.eventstream.EventStream.Publish
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.stella.brain.programs.ClassifiedProgramManager.ClassifiedProgramManagerMessage
import org.stella.brain.programs.ProgramClassifier.{ClassAndScore, ProgramClassifierMessage}
import org.stella.brain.programs.UntrainedProgramManager.UntrainedProgramManagerMessage

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
 *
 * Please note, for the whole system to work, an rSocket connection to send untrained data to & receive untrained data from user
 * should be created by the main class at the same time as this controller using RSocketUserManager
 */
// ideally, there would be one ProgramClassifier and one UntrainedProgramManager per client, however i don't know how to have one rsocket connection per user.
object ProgramController {

  sealed trait ProgramControllerMessage
  final case object ProgramByDateTick extends ProgramControllerMessage
  final case class ProgramsByDateAdapted(date: LocalDate, programs: List[Program]) extends ProgramControllerMessage
  final case class ProgramsClassificationAdapted(classifiedPrograms: List[(Program, ClassAndScore)]) extends ProgramControllerMessage

  /**
   * Creates a new ProgramController.
   * Only one instance per ActorSystem is expected so this actor should be spawned by the main program guardian.
   *
   * There are 2 main reasons why some actors are passed in as a parameter, and not spawned by ProgramController itself:
   * - they may be used by other components. Typically, untrainedProgramManager will also be used by UserManager.
   * - mocks may be passed in tests. This is especially useful to test eventStream.
   *
   * Supervision wise, it doesn't look wrong either to have the main program guardian as the parent of these actors, rather than ProgramController (as would be the case if they were spawned in here).
   *
   */
  def apply(programClassifier: ActorRef[ProgramClassifierMessage], untrainedProgramManager: ActorRef[UntrainedProgramManagerMessage], classifiedProgramManager: ActorRef[ClassifiedProgramManagerMessage], eventStream: ActorRef[EventStream.Command], programCache: ActorRef[ProgramCache.Command]): Behavior[ProgramControllerMessage] = {
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case ProgramByDateTick =>
          val date = LocalDate.now
          context.log.info("Received ProgramByDateTick")
          val adapter = context.messageAdapter[ProgramCollector.ProgramsByDate](response => ProgramsByDateAdapted(response.date, response.programs))
          context.spawn(ProgramCollector(ProgramCollector.TV_CHANNELS, programCache), "ProgramCollector") ! ProgramCollector.ProgramsByDateRequest(date, adapter)
          Behaviors.same
        case ProgramsByDateAdapted(date, programs) =>
          val programsClassificationAdapter = context.messageAdapter[ProgramClassifier.ProgramsClassification](response => ProgramsClassificationAdapted(response.classifications))
          programClassifier ! ProgramClassifier.ProgramsClassificationRequest(programs, programsClassificationAdapter)
          // extract summaries out of programs.
          untrainedProgramManager ! UntrainedProgramManager.UntrainedProgramsNotification(programs.map(_.summary))
          eventStream ! Publish(programs)
//          context.scheduleOnce(1.day, context.self, ProgramByDateTick) TODO use timer
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
