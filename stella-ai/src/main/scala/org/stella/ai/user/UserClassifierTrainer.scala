package org.stella.ai.user

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.SourceQueue
import org.stella.ai.tv.TvProgramClassifier._

// https://github.com/calvinlfer/websockets-pubsub-akka/

object UserClassifierTrainer {
  case object Ack
  case class ConnectionEstablished(wsHandle: SourceQueue[Message])
  case object ConnectionDropped

  def props() = {
    Props(new UserClassifierTrainer())
  }
}

protected class UserClassifierTrainer() extends Actor with ActorLogging {
  import org.stella.ai.user.UserClassifierTrainer._
  private var websocketHandle: SourceQueue[Message] = _

  override def receive: Receive = {
    // `wsHandle` is a handle to communicate back to the WebSocket user
    case ConnectionEstablished(wsHandle) =>
      websocketHandle = wsHandle
      context.system.eventStream.subscribe(self, classOf[AskUserDataTraining])
      // data training request/response
    case TextMessage.Strict(dataTraining) =>
      log.info(s"Received data training $dataTraining")
      context.system.eventStream.publish(SendClassifierDataTraining(List(dataTraining).map(_.split("=")).map{case Array(summary, rating) => (summary, rating)}))
    case AskUserDataTraining(programs) =>
      log.info(s"Asking data training for $programs")
      websocketHandle.offer(TextMessage.Strict(programs.map(_+"=").mkString("\n")))
  }
}
