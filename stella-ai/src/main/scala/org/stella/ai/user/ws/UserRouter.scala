package org.stella.ai.user.ws

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.http.scaladsl.server.Directives._
import org.stella.ai.tv.TvProgramClassifier.{SendClassifierDataTraining, UserTrainingConnectionDropped, UserTrainingConnectionEstablished}
import org.stella.ai.user.UserClassifierTester
import scala.concurrent.duration._

object UserRouter {

  def route(classifier: ActorRef)(implicit system: ActorSystem): server.Route = {
    path("classifier-training") {
      handleWebSocketMessages(getClassifierTrainerFlow(system, classifier))
    } ~
    path("classifier-testing") {
      handleWebSocketMessages(getClassifierTesterFlow(system, classifier))
    }
  }

  private def getClassifierTrainerFlow(system: ActorSystem, classifier: ActorRef): Flow[Message, Message, NotUsed] = {
    // Create an actor for every WebSocket connection, this will represent the contact point to reach the user

    val sink: Sink[Message, NotUsed] =
      Flow[Message]
      .collect {case TextMessage.Strict(trainedData) => SendClassifierDataTraining(trainedData.split("\n").map(_.split("=")).map { case Array(summary, rating) => (summary, rating) }.toList)}
        // TODO replace with actorRefWithAck
      .to(Sink.actorRef(classifier, UserTrainingConnectionDropped)) // connect to the wsUser Actor

    val source: Source[Message, NotUsed] =
      Source
        .queue[List[String]](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
        .map(untrainedData => TextMessage.Strict(untrainedData.mkString("\n")))
        .mapMaterializedValue { queue =>
          classifier ! UserTrainingConnectionEstablished(queue)
          // don't expose the queue anymore
          NotUsed
        }
        .keepAlive(maxIdle = 10.seconds, () => TextMessage.Strict("Keep-alive message sent to WebSocket recipient"))

    Flow.fromSinkAndSource(sink, source)
  }

  private def getClassifierTesterFlow(system: ActorSystem, classifier: ActorRef): Flow[Message, Message, NotUsed] = {
    import org.stella.ai.user.UserClassifierTester._
    // Create an actor for every WebSocket connection, this will represent the contact point to reach the user
    val classifierTester: ActorRef = system.actorOf(UserClassifierTester.props(classifier), "user-classifier-tester")

    val sink: Sink[Message, NotUsed] =
      Flow[Message]
        .collect {case TextMessage.Strict(userMessage) => userMessage}
        // TODO replace with actorRefWithAck
        .to(Sink.actorRef(classifierTester, ConnectionDropped)) // connect to the classifierTester Actor

    val source: Source[Message, NotUsed] =
    Source
        .queue[Message](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
        .mapMaterializedValue { queue =>
          classifierTester ! ConnectionEstablished(queue)
          // don't expose the queue anymore
          NotUsed
        }
        .keepAlive(maxIdle = 10.seconds, () => TextMessage.Strict("Keep-alive message sent to WebSocket recipient"))

    Flow.fromSinkAndSource(sink, source)
  }

}
