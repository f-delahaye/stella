package org.stella.brain.user

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.rsocket._
import io.rsocket.metadata.{RoutingMetadata, WellKnownMimeType}
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.DefaultPayload
import org.reactivestreams.Publisher
import org.stella.brain.programs.ProgramClassifier.Trained
import org.stella.brain.programs.UntrainedProgramManager.Untrained
import reactor.core.publisher.{Flux, Mono}


/**
 * A high level api to communicate with user over RSocket.
 *
 * This is NOT meant to be a generic & configurable RSocket layer so methods and parameters are tailor made for Stella.
 */
trait RSocketServer {

  type Handler[I, O] = (Sink[I, _], Source[O, _])

  /**
   * Creates a channel where untrained programs are sent to user and trained programs are received from user.
   */
  def programTrainingChannel(handler: Handler[Trained, Untrained]): Unit
}

object RSocketServer extends RSocketServer{

  var programTrainingChannelHandler: Handler[Trained, Untrained] = _

  def programTrainingChannel(handler: Handler[Trained, Untrained]): Unit = programTrainingChannelHandler = handler

  def extractRoute(payload: Payload): Option[String] = {
    import io.rsocket.metadata.CompositeMetadata

    import scala.jdk.CollectionConverters._
    val metadata = new CompositeMetadata(payload.metadata, false).asScala
    val routeMetadata = metadata.find(entry => entry.getMimeType == WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.getString)
    routeMetadata.map(entry => new RoutingMetadata(entry.getContent).iterator().next())
  }

  def start()(implicit materializer: Materializer): Unit = {
    RSocketFactory.receive()
      .acceptor((_, _) => createSocket())
      .transport(TcpServerTransport.create(7878))
      .start()
      // TODO not clear if this flavor of subscribe or one with a consumer and/or error handler should be used
      .subscribe()

    def createSocket(): Mono[RSocket] = {
      //  override def accept(setup: ConnectionSetupPayload, sendingSocket: RSocket): Mono[RSocket] = {

      val socket = new AbstractRSocket() {
        System.out.println("New RSocket")

        private def handle(first: Payload, all: Flux[Payload]): Flux[Payload] =
          extractRoute(first) match {
            case Some("program.training") =>
              System.out.println("Routing to program training")
              val source = Source.fromPublisher(all).map(_.getDataUtf8).map(_.split("=")).map(split => {
                val trained = (split(0).trim, split(1).trim)
                System.out.println(s"[RSocketServer] Received trained $trained")
                trained
              })
              programTrainingChannelHandler._1.runWith(source)
              Flux.from(programTrainingChannelHandler._2.map(DefaultPayload.create).runWith(Sink.asPublisher(false))(materializer))
            case None => Flux.empty()
          }


        override def requestChannel(payloads: Publisher[Payload]): Flux[Payload] =
        // https://github.com/rsocket/rsocket-java/issues/569 seems to provide an interesting alternative to the SwitchOnFirst transformer,
        // but is not a standard api.
        // So we're sticking with switchOnFirst even though the javadoc states that the return should be based off the all parameter,
        // which as far as i understand is not a requirement of RSocket (the returned Flux may be nothing to do with the one passed in)
          Flux.from(payloads).switchOnFirst((signal, all) => Option(signal.get()).map(handle(_, all)).getOrElse(all))
      }

      Mono.just(socket)
    }
  }


}

