package org.stella.ai.user

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration._

// https://github.com/calvinlfer/websockets-pubsub-akka/

object UserRouter {

  def route(implicit system: ActorSystem): server.Route = {
    path("classifier-training") {
      handleWebSocketMessages(getClassifierTrainerFlow(system))
    } ~
    path("classifier-testing") {
      handleWebSocketMessages(getClassifierTesterFlow(system))
    }
  }

  private def getClassifierTrainerFlow(system: ActorSystem): Flow[Message, Message, NotUsed] = {
    import org.stella.ai.user.UserClassifierTrainer._
    // Create an actor for every WebSocket connection, this will represent the contact point to reach the user
    val wsUser: ActorRef = system.actorOf(UserClassifierTrainer.props())

    val sink: Sink[Message, NotUsed] =
      Flow[Message]
        // TODO replace with actorRefWithAck
        .to(Sink.actorRef(wsUser, ConnectionDropped)) // connect to the wsUser Actor

    val source: Source[Message, NotUsed] =
      Source
        .queue[Message](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
        .mapMaterializedValue { wsHandle =>
          // the wsHandle is the way to talk back to the user, our wsUser actor needs to know about this to send
          // messages to the WebSocket user
          wsUser ! ConnectionEstablished(wsHandle)
          // don't expose the wsHandle anymore
          NotUsed
        }
        .keepAlive(maxIdle = 10.seconds, () => TextMessage.Strict("Keep-alive message sent to WebSocket recipient"))

    Flow.fromSinkAndSource(sink, source)
  }

  private def getClassifierTesterFlow(system: ActorSystem): Flow[Message, Message, NotUsed] = {
    import org.stella.ai.user.UserClassifierTester._
    // Create an actor for every WebSocket connection, this will represent the contact point to reach the user
    val wsUser: ActorRef = system.actorOf(UserClassifierTester.props())

    val sink: Sink[Message, NotUsed] =
      Flow[Message]
        .collect {case TextMessage.Strict(userMessage) => userMessage}
        // TODO replace with actorRefWithAck
        .to(Sink.actorRef(wsUser, ConnectionDropped)) // connect to the wsUser Actor

    val source: Source[Message, NotUsed] =
    Source
        .queue[Message](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
        .mapMaterializedValue { wsHandle =>
          // the wsHandle is the way to talk back to the user, our wsUser actor needs to know about this to send
          // messages to the WebSocket user
          wsUser ! ConnectionEstablished(wsHandle)
          // don't expose the wsHandle anymore
          NotUsed
        }
        .keepAlive(maxIdle = 10.seconds, () => TextMessage.Strict("Keep-alive message sent to WebSocket recipient"))

    Flow.fromSinkAndSource(sink, source)
  }

}
