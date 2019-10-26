package org.stella.ai.tv

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.scaladsl.SourceQueue
import org.stella.ai.classifier.{RetrainableClassifier, StanfordClassifier, TextClassifier}

object TvProgramClassifier {
  //  https://doc.akka.io/docs/akka/current/actors.html#recommended-practices
  def props(classifier: TextClassifier): Props = Props(new TvProgramClassifier(classifier))
  def props: Props = props(new StanfordClassifier(Some("src/main/resources/tv-programs")) with RetrainableClassifier)

  case class UserTrainingConnectionEstablished(handle: SourceQueue[List[String]])
  case object UserTrainingConnectionDropped

  // Message sent by client to classifier to request a selection off the supplied programs
  case class AskClassifierTvProgramsSelection(programs: List[TvProgram])
  // Message sent by classifier to client in response to a selection request
  case class SendClientTvProgramsSelection(selection: List[TvProgram])

  // USER FEEDBACK
  // Message sent by user in response to a user trained data request sent directly to the queue
  case class SendClassifierDataTraining(trainedData: List[(String, String)])

  // TESTS
  // Message sent by client to get the score of the supplied summary. This is currently intended for test purposes only
  case class AskClassifierRatingAndScore(summary: String)
  // Message sent by classifier in response to a rating and score request
  case class SendUserRatingAndScores(summary: String, rating: String, score: String)
}

/**
  * Actor which is responsible for loading a classifier and use it to classify TvPrograms upon requests.
  * The classifier will be trained using data from the user.
  *
  * TvProgramClassifier is *not* an akka singleton but since it is responsible for maintaining the classifier, in effect there must be only one instance.
  *
  */
protected class TvProgramClassifier(val classifier: TextClassifier) extends Actor with ActorLogging {

  var userDataTrainingConnection: Option[SourceQueue[List[String]]] = None

  import TvProgramClassifier._

  override def receive: Receive = {
    case UserTrainingConnectionEstablished(_queue) =>
      userDataTrainingConnection = Some(_queue)
    case UserTrainingConnectionDropped =>
      userDataTrainingConnection = None
    // data training request/response
    case AskClassifierTvProgramsSelection(programs) =>
      val retained = programs.filter(isSelectable)
      sender ! SendClientTvProgramsSelection(retained)
      // randomly select some items and send them back to user for training.
      // it is very important that these items contain both retained and discarded programs
      userDataTrainingConnection.fold(())(queue => queue.offer(programs.slice(0, 10).map(_.summary)))
    case SendClassifierDataTraining(programs) =>
      classifier.add(programs)
    case AskClassifierRatingAndScore(summary) =>
      val (rating, score) = classifier.ratingAndScore(summary)
      sender ! SendUserRatingAndScores(summary, rating, score.toString)
  }

  private def isSelectable(program: TvProgram): Boolean = {
    val (rating, score) = classifier.ratingAndScore(program.summary)
// we could pass the threshold as a parameter of the message ...
    "yes".equals(rating) && score > 0.5
  }
}
