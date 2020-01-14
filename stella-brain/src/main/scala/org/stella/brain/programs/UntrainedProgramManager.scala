package org.stella.brain.programs

import akka.actor.typed.eventstream.EventStream.Publish
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

/**
 * Keeps track of untrained programs within the system.
 * There should be one instance of UntrainedProgramManager per user supported by Stella.
 *
 * Untrained data will typically come from collected - but not yet trained - data, and are expected to be sent to user for manual training.
 * This manager stores a subset of untrained data that may be requested when users connect to the system to get an initial snapshot.
 * In addition to this, all untrained data notified to this actor will be forwarded on to the event bus where users may have registered themselves to get updates.
 *
 * Once trained, untrained data will be sent to the classifier, not back to this manager.
 *
 * As a rule of thumb, untrained data can safely be dropped, either because the internal buffer is limited in size, or because the actor is restarted.
 * It is expected that new untrained data will come in on a regular basis and if some are dropped, it won't significantly impact the classifier.
 *
 * Its protocol matches this:
 * - untrainedDataRequest's are issued by user when they connect to request data that they will train.
 * - untrainedDataNotification's are issued by system when new data are collected but not yet trained
 * - untrainedData are sent back to user upon untrainedDataRequest's
 *
 */
object UntrainedProgramManager {

  sealed trait UntrainedProgramManagerMessage
  type Untrained = String // only the summary is needed to train untrained data.

  // Client issuing a request to get all untrained data that this manager knows about since the specified timestamp.
  final case class UntrainedProgramsRequest(since: Long, replyTo: ActorRef[UntrainedPrograms]) extends UntrainedProgramManagerMessage

  // client notifying this classifier that new untrained data have been collected
  final case class UntrainedProgramsNotification(untrainedPrograms: List[Untrained]) extends UntrainedProgramManagerMessage

  final case class UntrainedPrograms(untrainedPrograms: List[Untrained])

  private def handle(untrainedPrograms: List[Untrained]): Behavior[UntrainedProgramManagerMessage] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case UntrainedProgramsRequest(since, replyTo) =>
          replyTo ! UntrainedPrograms(untrainedPrograms)
          Behaviors.same
        case UntrainedProgramsNotification(newUntrainedPrograms) =>
          publishToEventStream(context.system, newUntrainedPrograms)
          handle(untrainedPrograms ::: newUntrainedPrograms)
      }
    }

  def apply(): Behavior[UntrainedProgramManagerMessage] = handle(List.empty)

  // A convenience method which should be used by the unit test which validates the listening actor.
  // Hence that test should look like:
  // ActorRef subscriberProbe = TestProbe...
  // UntrainedProgramManager.publishToEventStream(...)
  // expectMessage(subscriberProbe.UntrainedData(...))
  def publishToEventStream(system: ActorSystem[_], untrained: List[Untrained]): Unit = system.eventStream ! Publish(UntrainedPrograms(untrained))
}
