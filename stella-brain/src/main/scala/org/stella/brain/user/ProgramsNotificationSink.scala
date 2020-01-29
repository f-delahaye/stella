package org.stella.brain.user

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.stella.brain.programs.ProgramClassifier.{ProgramClassifierMessage, Trained, TrainedProgramsNotification}

/**
 * An actor which will typically be used in an ActorSink. It handles messages inherent to a sink (ack, completed, failure, ...)
 * so as to keep the business actors as clean as possible.
 *
 * All business messages are passed to their corresponding managers / listeners
 */
object ProgramsNotificationSink {
  sealed trait ProgramsNotificationSinkMessage
  final case object TrainedProgramsNotificationComplete extends ProgramsNotificationSinkMessage
  final case class TrainedProgramNotificationReceived(trainedProgram: Trained) extends ProgramsNotificationSinkMessage

  def apply(programClassifier: ActorRef[ProgramClassifierMessage]): Behavior[ProgramsNotificationSinkMessage] = {
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case TrainedProgramsNotificationComplete =>
          System.out.println("[ProgramsNotificationSink] TrainedPrograms notification complete")
          Behaviors.same
        case TrainedProgramNotificationReceived(trainedProgram) =>
          System.out.println("[ProgramsNotificationSink] TrainedPrograms notification received")
          programClassifier ! TrainedProgramsNotification(List(trainedProgram))
          Behaviors.same
      }
    }
  }
}
