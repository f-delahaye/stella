package org.stella.ai.user

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.SourceQueue
import org.stella.ai.tv.TvProgramClassifier.{AskClassifierRatingAndScore, AskClassifierTvProgramsSelection, SendClientTvProgramsSelection, SendUserRatingAndScores}
import org.stella.ai.tv.TvProgramCollector
import org.stella.ai.tv.TvProgramCollector.{CollectPrograms, ProgramsCollected}

object UserClassifierTester {
  case object Ack
  case class ConnectionEstablished(wsHandle: SourceQueue[Message])
  case object ConnectionDropped

  def props(classifier: ActorRef): Props = {
    Props(new UserClassifierTester(classifier))
  }
}

class UserClassifierTester(val classifier: ActorRef) extends Actor with ActorLogging {
  import UserClassifierTester._

  private val collector = context.actorOf(TvProgramCollector.props)

  private var queue: SourceQueue[Message] = _

  override def receive: Receive = {
    case ConnectionEstablished(_queue) =>
      this.queue = _queue
    // data training request/response
    // RatingAndScores request/response
    case userMessage: String if userMessage.startsWith("rating-and-scores\n") =>
      val summary = userMessage.substring("rating-and-scores\n".length)
      log.info(s"rating and scores requested for $summary")
      classifier ! AskClassifierRatingAndScore(summary)
//    case userMessage: String if userMessage.startsWith("selection\n") =>
      // expects a list of summaries, once per line. Each will be used to create a TvProgram (setting all other fields to empty string) and the classifier will be called to see which ones are retained, and which ones are discarded
//      val summaries = userMessage.substring("selection\n".length)
//      classifier ! AskClassifierTvProgramsSelection(summaries.split("\n").map(TvProgram(LocalTime.now(), "", "", _)).toList)
    case userMessage: String if userMessage.startsWith("check-date\n") =>
      val date = LocalDate.parse(userMessage.substring("check-date\n".length), DateTimeFormatter.ofPattern("dd.MM.yyyy"))
       collector ! CollectPrograms(date)
      // Call ProgramCollector
    case SendUserRatingAndScores(summary, rating, scores) =>
      queue.offer(TextMessage.Strict(s"$summary has rating $rating with scores $scores"))
      // May be received upon a CheckDate call triggered above
    case ProgramsCollected(programs) =>
      classifier ! AskClassifierTvProgramsSelection(programs._2)
    case SendClientTvProgramsSelection(programs) =>
    queue.offer(TextMessage.Strict("selection is\n"+programs.map(_.summary).mkString("\n")))
  }
}
