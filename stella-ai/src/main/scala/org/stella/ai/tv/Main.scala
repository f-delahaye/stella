package org.stella.ai.tv

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.stella.ai.user.grpc.{UserServiceHandler, UserServiceImpl}
import org.stella.ai.user.ws.UserRouter

import scala.concurrent.ExecutionContext
import scala.util._

object Main extends App {

    // Important: grpc requires that we enable HTTP/2 in ActorSystem's config
    // We do it here programmatically, but you can also set it in the application.conf
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())

    //private val igniteCfg = new IgniteConfiguration()
    //igniteCfg.setPersistentStoreConfiguration(new PersistentStoreConfiguration())
    //private val dataStorageConfiguration = new DataStorageConfiguration()
    //  dataStorageConfiguration.getDefaultDataRegionConfiguration().setPersistenceEnabled(true)
    //igniteCfg.setDataStorageConfiguration(dataStorageConfiguration)

    //val ignite = Ignition.start(igniteCfg);
    // ignite.active(true)

    implicit val system = ActorSystem("Stella", conf)
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContext = system.dispatcher

    // Main brain area
    //  private val tvProgramArea = system.actorOf(Props(new TvProgramArea()))

    // Ideally, I'd like to have tvProgramClassifier created within TvProgramArea.
    // but then, I don't know how to make UserRouter / Actors aware of it:
    // - creating a new actor is not possible since by design TvProgramClassifier is expected to be a singleton
    // - looking up its path could be possible but not encouraged by akka
    // - only other solution would be to publish / subscribe messages between UserActor and TvProgramClassifier but that looks like an even worse evil
    private val tvProgramClassifier =
    system.actorOf(TvProgramClassifier.props)

    runwithGrpc.onComplete {
      case Success(binding) =>
        println(s"Server online at ws://${binding.localAddress.getHostName}:${binding.localAddress.getPort}\n")

      case Failure(ex) =>
        println(s"Failed to start server, shutting down actor system. Exception is: ${ex.getCause}: ${ex.getMessage}")
        system.terminate()
    }

  private def runwithGrpc = {
    // Bind service handler servers to localhost:8080/8081
    Http().bindAndHandleAsync(
      UserServiceHandler(new UserServiceImpl(tvProgramClassifier)),
      interface = "127.0.0.1",
      port = 8080,
      connectionContext = HttpConnectionContext())
  }

  private def runWithWebsocket = {
    Http().bindAndHandle(UserRouter.route(tvProgramClassifier), "127.0.0.1", 9001)
  }
}