package org.stella.brain.user

import akka.stream.scaladsl.Sink
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.DefaultPayload
import io.rsocket.{AbstractRSocket, ConnectionSetupPayload, Payload, RSocket, RSocketFactory, SocketAcceptor}
import reactor.core.publisher.{Flux, Mono}

import scala.collection.mutable


/**
 * A bidirectional connection from / to user.
 */
trait RSocketServer {
  def addRoute(route: String, handler: RSocket): Unit
  def removeRoute(route: String): Unit
}

object RSocketServer extends RSocketServer with SocketAcceptor {
  val routes = mutable.Map[String, Mono[RSocket]]()

  override def addRoute(route: String, handler: RSocket): Unit = routes.put(route, Mono.just(handler))

  override def removeRoute(route: String): Unit = routes.remove(route)

  override def accept(setup: ConnectionSetupPayload, sendingSocket: RSocket): Mono[RSocket] = {


    def extractRoute(): String = {
     // TODO use Metadata type. For the time being, we only support one route
      "program.untrained"
    }
    System.out.println(s"Request received with payload $setup")
    val route = extractRoute()
    val router = routes.getOrElse(route, Mono.empty())
    System.out.println(s"Using router $router")
    router
  }

  def start() = RSocketFactory.receive()
    .acceptor(this)
    .transport(TcpServerTransport.create(7878))
    .start()
    // TODO not clear if this flavor of subscribe or one with a consumer and/or error handler should be used
    .subscribe()


}

