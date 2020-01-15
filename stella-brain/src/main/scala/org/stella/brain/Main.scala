package org.stella.brain

import akka.NotUsed
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.stella.brain.programs.{ClassifiedProgramManager, ProgramClassifier, ProgramController, ProgramGuardian, UntrainedProgramManager}

object Main {

  def apply(): Behavior[NotUsed] =
    Behaviors.setup { context =>
      // Not clear if there should be one instance of the following actors per ActorSystem, or one per user...
      context.spawn(ProgramGuardian(), "ProgramGuardian")
      Behaviors.empty
    }

  def main(args: Array[String]): Unit = {
    ActorSystem(Main(), "StellaBrain")
  }
}
