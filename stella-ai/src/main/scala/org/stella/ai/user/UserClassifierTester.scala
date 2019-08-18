package org.stella.ai.user

import akka.actor.{Actor, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.SourceQueue
import org.stella.ai.tv.TvProgramClassifier.{AskClassifierRatingAndScore, SendUserRatingAndScores}

object UserClassifierTester {
  case object Ack
  case class ConnectionEstablished(wsHandle: SourceQueue[Message])
  case object ConnectionDropped

  def props() = {
    Props(new UserClassifierTester())
  }
}

class UserClassifierTester extends Actor{
  import UserClassifierTester._
  private var queue: SourceQueue[Message] = _

  override def receive: Receive = {
    case ConnectionEstablished(q) =>
      this.queue = q
      context.system.eventStream.subscribe(self, classOf[SendUserRatingAndScores])
    // data training request/response
    // RatingAndScores request/response
    case TextMessage.Strict(summary) =>
      context.system.eventStream.publish(AskClassifierRatingAndScore(summary))
    case SendUserRatingAndScores(summary, rating, scores) =>
      queue.offer(TextMessage.Strict(s"$summary has rating $rating with scores $scores"))
  }
}
