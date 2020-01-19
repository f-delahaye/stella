package org.stella.brain.programs

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.Sink
import io.rsocket.{AbstractRSocket, Payload}
import io.rsocket.util.DefaultPayload
import org.stella.brain.user.{RSocketServer, UntrainedDataUserFlow}
import reactor.core.publisher.Flux

/**
 * The main program actor.
 *
 * Acts as the guardian of the program actors hierarchy
 */
object ProgramMain {

  def apply(server: RSocketServer): Behavior[NotUsed] =
    Behaviors.setup { context =>

      implicit val actorSystem: ActorSystem[Nothing] = context.system

      // Not clear if there should be one instance of the following actors per ActorSystem, or one per user...
      val programClassifier = context.spawn(ProgramClassifier(), "ProgramClassifier")
      val untrainedProgramManager = context.spawn(UntrainedProgramManager(), "UntrainedProgramManager")
      val classifiedProgramManager = context.spawn(ClassifiedProgramManager(), "ClassifiedProgramManager")
      val programController = context.spawn(ProgramController(programClassifier, untrainedProgramManager, classifiedProgramManager, context.system.eventStream), "ProgramController")

      def programTrainingRSocket() =        new AbstractRSocket() {
        override def requestStream(payload: Payload): Flux[Payload] =
          Flux.from(UntrainedDataUserFlow.createSource(24, untrainedProgramManager)
            .map(DefaultPayload.create)
            .runWith(Sink.asPublisher(false)))
      }

      server.addRoute("program.untrained", programTrainingRSocket())
      //TODO observe untrainedProgramManager and remove route upon its termination
     programController ! ProgramController.ProgramByDateTick

      Behaviors.empty

    }
}
