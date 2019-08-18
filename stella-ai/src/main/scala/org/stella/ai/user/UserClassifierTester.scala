package org.stella.ai.user

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.SourceQueue
import org.stella.ai.tv.TvProgramClassifier.{AskClassifierRatingAndScore, SendUserRatingAndScores}

object UserClassifierTester {
  case object Ack
  case class ConnectionEstablished(wsHandle: SourceQueue[Message])
  case object ConnectionDropped

  def props(): Props = {
    Props(new UserClassifierTester())
  }
}

class UserClassifierTester extends Actor with ActorLogging {
  import UserClassifierTester._
  private var wsQueue: SourceQueue[Message] = _

  override def receive: Receive = {
    case ConnectionEstablished(queue) =>
      this.wsQueue = queue
      context.system.eventStream.subscribe(self, classOf[SendUserRatingAndScores])
    // data training request/response
    // RatingAndScores request/response
    case userMessage: String if userMessage.startsWith("rating-and-scores:") =>
      val summary = userMessage.substring("rating-and-scores:".length)
      log.info(s"rating and scores requested for $summary")
      context.system.eventStream.publish(AskClassifierRatingAndScore(summary))
    case SendUserRatingAndScores(summary, rating, scores) =>
      wsQueue.offer(TextMessage.Strict(s"$summary has rating $rating with scores $scores"))
  }
}
