package org.stella.brain.programs

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}

/**
 * The main application.
 *
 * Acts as the guardian of the program actors hierarchy
 */
object ProgramGuardian {
  def apply(): Behavior[NotUsed] =
    Behaviors.setup { context =>
      // Not clear if there should be one instance of the following actors per ActorSystem, or one per user...
      val programClassifier = context.spawn(ProgramClassifier(), "ProgramClassifier")
      val untrainedProgramManager = context.spawn(UntrainedProgramManager(), "UntrainedProgramManager")
      val classifiedProgramManager = context.spawn(ClassifiedProgramManager(), "ClassifiedProgramManager")
      val programController = context.spawn(ProgramController(programClassifier, untrainedProgramManager, classifiedProgramManager, context.system.eventStream), "ProgramController")

      programController ! ProgramController.ProgramByDateTick

      Behaviors.empty
    }


}
