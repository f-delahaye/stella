package org.stella.brain.app

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import org.stella.brain.programs.LInternauteOverviewCrawler.RequestMode
import org.stella.brain.programs.ProgramMain
import org.stella.brain.user.RSocketServer

object StellaServer {

  def apply(): Behavior[NotUsed] =
    Behaviors.setup { context =>
      context.spawn(ProgramMain(RSocketServer), "ProgramMain")
      Behaviors.empty
    }

  def main(args: Array[String]): Unit = {
    val testMode = args.size > 0 && args(0).equalsIgnoreCase("test")
    if (testMode) {
      StellaConfig.setLInternauteRequestMode(RequestMode.TestFile)
    }
    System.out.println(s"Starting Stella in test mode $testMode")
    implicit val system = ActorSystem(StellaServer(), "StellaBrain")

    System.out.println("Starting RSocket server")
    new Thread(() => RSocketServer.start).start()
  }
}
