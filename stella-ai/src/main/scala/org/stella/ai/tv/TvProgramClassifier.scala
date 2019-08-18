package org.stella.ai.tv

import java.util.Properties

import akka.actor.{Actor, ActorLogging, Props}
import edu.stanford.nlp.classify.{Classifier, ColumnDataClassifier, Dataset}
import org.stella.ai.tv.TvProgramClassifier.{AskClassifierRatingAndScore, SendClassifierDataTraining}

object TvProgramClassifier {
  //  https://doc.akka.io/docs/akka/current/actors.html#recommended-practices
  def props(): Props = Props(new TvProgramClassifier())

  // Message sent by client to classifier to request a selection off the supplied programs
  case class AskClassifierTvProgramsSelection(programs: List[TvProgram])
  // Message sent by classifier to client in response to a selection request
  case class SendClientTvProgramsSelection(selection: List[TvProgram])

  // USER INTERACTION
  // Message sent by classifier to request user trained data
  case class AskUserDataTraining(programs: List[TvProgram])
  // Message sent by user in response to a user trained data request
  case class SendClassifierDataTraining(trainedProgram: List[(String, String)])

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
protected class TvProgramClassifier extends Actor with ActorLogging {

  val cdc = new ColumnDataClassifier(buildProperties())
  val trainedData: Dataset[String,String] = new Dataset[String,String]()

  context.system.eventStream.subscribe(self, classOf[AskClassifierRatingAndScore])
  context.system.eventStream.subscribe(self, classOf[SendClassifierDataTraining])

  var _classifier: Classifier[String, String] = _
  import TvProgramClassifier._

  override def receive: Receive = {
    case AskClassifierTvProgramsSelection(programs) =>
      val (selected, discarded) = programs.partition(isSelectable)
      sender() ! SendClientTvProgramsSelection(selected)
      context.system.eventStream.publish(AskUserDataTraining(discarded))
    case SendClassifierDataTraining(programs) =>
      programs.map { case (feature, rating) => programToDatum(feature, rating) }.foreach(trainedData.add)
      _classifier = null
    case AskClassifierRatingAndScore(summary) =>
      val datum = programToDatum(summary, "")
      context.system.eventStream.publish(SendUserRatingAndScores(summary, classifier.classOf(datum), classifier.scoresOf(datum).toString))
  }

  // ideally, classifier would be a lazy val. However:
  // - lazy are synchronous which is not needed here since we are within an actor
  // - more of an issue is that we need _classifier to be mutable. Alternate solution would be to kill the actor when a refresh of the classifier is needed but that really seems like an overkill
  private def classifier = {
    if (_classifier == null) {
      _classifier = cdc.makeClassifier(trainedData)
    }
    _classifier
  }

  private def isSelectable(program: TvProgram): Boolean = {
    val programDatum = programToDatum(program.summary, "")
    "yes".equals(classifier.classOf(programDatum)) // we could also take a score threshold into account
  }

  private def buildProperties() = {
    val props = new Properties()
    props.put("useClassFeature", "true")
    props.put("displayedColumn", "1")
    props.put("goldAnswerColumn", "0")
    props.put("1.useNGrams", "true")
    props.put("1.usePrefixSuffixNGrams", "true")
    props.put("1.maxNGramLeng", "4")
    props.put("1.minNGramLeng", "1")
    props
  }

  private def programToDatum(feature: String, rating: String) = {
    cdc.makeDatumFromLine(rating+"\t"+feature)
  }

}
