package org.stella.ai.tv

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import org.stella.ai.user.UserRouter

import scala.concurrent.ExecutionContext
import scala.util._

object Main extends App {

  //private val igniteCfg = new IgniteConfiguration()
  //igniteCfg.setPersistentStoreConfiguration(new PersistentStoreConfiguration())
  //private val dataStorageConfiguration = new DataStorageConfiguration()
//  dataStorageConfiguration.getDefaultDataRegionConfiguration().setPersistenceEnabled(true)
  //igniteCfg.setDataStorageConfiguration(dataStorageConfiguration)

  //val ignite = Ignition.start(igniteCfg);
 // ignite.active(true)

  implicit val system = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext   = system.dispatcher

  // Main brain area
  val tvProgramArea = system.actorOf(Props(new TvProgramArea()))

  Http().bindAndHandle(UserRouter.route, "127.0.0.1", 9001).onComplete {
    case Success(binding) =>
      println(s"Server online at ws://${binding.localAddress.getHostName}:${binding.localAddress.getPort}\n")

    case Failure(ex) =>
      println(s"Failed to start server, shutting down actor system. Exception is: ${ex.getCause}: ${ex.getMessage}")
      system.terminate()
  }
}