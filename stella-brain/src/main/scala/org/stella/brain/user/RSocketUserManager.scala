package org.stella.brain.user

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Sink
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.DefaultPayload
import io.rsocket.{AbstractRSocket, Payload, RSocketFactory}
import org.stella.brain.programs.ProgramClassifier.ProgramClassifierMessage
import reactor.core.publisher.{Flux, Mono}


/**
  * User manager over rsocket which:
  * - is given a Stream of untrained data to be sent to clients
  * - listens for events published by ProgramArea and sent untrained data to that stream for user training
  * - fires trained data notifications to the classifier as they come along from user
  *
  * There's one instance of this manager per user connection.
  * If no user is connected, there will be no instance.
  *
  * Unntrained data are received in a asynchronous way using akkka's bus event as opposed to messages for better decoupling
  */
object RSocketUserManager {

  def apply(implicit programClassifier: ActorRef[ProgramClassifierMessage], actorSystem: ActorSystem[Any]) = {
    RSocketFactory.receive()
      .acceptor(((setup, sendingSocket) => Mono.just(
        new AbstractRSocket() {
          override def requestStream(payload: Payload): Flux[Payload] = {
            Flux.from(UntrainedDataUserFlow.createSource(24, programClassifier)
              .map(DefaultPayload.create)
              .runWith(Sink.asPublisher(false)))
          }
        }
      )))
      .transport(TcpServerTransport.create(7878))
      .start()
      .block()
      .onClose()
      .block()
  }
}

