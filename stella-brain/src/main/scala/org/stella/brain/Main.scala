package org.stella.brain

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory
import org.stella.brain.programs.ProgramMain
import org.stella.brain.user.RSocketServer

object Main {

  def apply(): Behavior[NotUsed] =
    Behaviors.setup { context =>
      context.spawn(ProgramMain(RSocketServer), "ProgramGuardian")
      Behaviors.empty
    }

  def main(args: Array[String]): Unit = {
    val testMode = args.size > 0 && args(0).equalsIgnoreCase("test")
    val conf = ConfigFactory
      .parseString(if (testMode) "stella.brain.test=true" else "stella.brain.test=false")
      .withFallback(ConfigFactory.defaultApplication())
    ActorSystem(Main(), "StellaBrain", conf)
  }
}
