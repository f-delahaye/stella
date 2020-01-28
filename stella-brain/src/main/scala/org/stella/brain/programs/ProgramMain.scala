package org.stella.brain.programs

import akka.NotUsed
import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import org.stella.brain.programs.ProgramClassifier.{Trained, TrainedProgramsNotification}
import org.stella.brain.programs.UntrainedProgramManager.UntrainedPrograms
import org.stella.brain.user.RSocketServer

/**
 * The main program actor.
 *
 * Acts as the guardian of the program actors hierarchy
 */
object ProgramMain {
  private val CompletedTrainedProgramsNotification = TrainedProgramsNotification(List.empty)
  private val FailedTrainedProgramsNotification = TrainedProgramsNotification(List.empty)

  def apply(server: RSocketServer): Behavior[NotUsed] =
    Behaviors.setup { context =>

      // Not clear if there should be one instance of the following actors per ActorSystem, or one per user...
      val programClassifier = context.spawn(ProgramClassifier(), "ProgramClassifier")
      val untrainedProgramManager = context.spawn(UntrainedProgramManager(), "UntrainedProgramManager")
      val classifiedProgramManager = context.spawn(ClassifiedProgramManager(), "ClassifiedProgramManager")
      val programController = context.spawn(ProgramController(programClassifier, untrainedProgramManager, classifiedProgramManager, context.system.eventStream), "ProgramController")

      server.programTrainingChannel((trainedProgramsSink(programClassifier), untrainedProgramsSource(24, untrainedProgramManager, context.system.eventStream)))
      //TODO observe untrainedProgramManager and remove route upon its termination

     programController ! ProgramController.ProgramByDateTick

      Behaviors.empty
    }

  private def trainedProgramsSink(programClassifier: ActorRef[TrainedProgramsNotification]): Sink[Trained, _] =
    ActorSink.actorRef[TrainedProgramsNotification](programClassifier, CompletedTrainedProgramsNotification, exc => FailedTrainedProgramsNotification)
      .contramap(trained => {
        System.out.println(s"Received training $trained")
        TrainedProgramsNotification(List(trained))
      })

  /**
   * Returns an Akka source which generates Untrained data that may be sent to user for manual classification.
   *
   * Untrained data are represented as simple strings.
   * Data retrieved consists of:
   * - an initial list of untrained data which are stored internally for a short period of time (typically a few days worth of data)
   * - any new untrained data collected AFTER the source has been created.
   */
  private def untrainedProgramsSource(hoursSinceLastConnection: Long, untrainedProgramManager: ActorRef[UntrainedProgramManager.UntrainedProgramManagerMessage], eventStream: ActorRef[EventStream.Command]): Source[String, _] = {
    ActorSource.actorRef[UntrainedPrograms](PartialFunction.empty,PartialFunction.empty, 10, OverflowStrategy.dropHead)
      .mapConcat(_.untrainedPrograms)// extract list from message
      .mapMaterializedValue(
        actorRef => {
          untrainedProgramManager ! UntrainedProgramManager.UntrainedProgramsRequest(hoursSinceLastConnection, actorRef)
          eventStream ! EventStream.Subscribe(actorRef)
        })
  }


}
