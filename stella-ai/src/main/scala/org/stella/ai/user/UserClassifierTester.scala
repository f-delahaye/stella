package org.stella.ai.user

import java.time.{LocalDate, LocalTime}

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.SourceQueue
import org.stella.ai.tv.TvProgram
import org.stella.ai.tv.TvProgramClassifier.{AskClassifierRatingAndScore, AskClassifierTvProgramsSelection, SendUserRatingAndScores}

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
    case userMessage: String if userMessage.startsWith("rating-and-scores\n") =>
      val summary = userMessage.substring("rating-and-scores\n".length)
      log.info(s"rating and scores requested for $summary")
      context.system.eventStream.publish(AskClassifierRatingAndScore(summary))
    case userMessage: String if userMessage.startsWith("selection\n") =>
      // expects a list of summaries, once per line. Each will be used to create a TvProgram (setting all other fields to empty string) and the classifier will be called to see which ones are retained, and which ones are discarded
      val summaries = userMessage.substring("selection\n".length)
      context.system.eventStream.publish(AskClassifierTvProgramsSelection(self, summaries.split("\n").map(TvProgram(LocalTime.now(), "", "", _)).toList))
    case SendUserRatingAndScores(summary, rating, scores) =>
      wsQueue.offer(TextMessage.Strict(s"$summary has rating $rating with scores $scores"))
  }
}
