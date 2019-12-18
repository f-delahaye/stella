package org.stella.brain.programs

import java.io.IOException
import java.util.Properties

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import edu.stanford.nlp.classify._
import edu.stanford.nlp.io.IOUtils

/**
  * Takes a list of unclassified programs and classifies them.
  *
  */
object ProgramClassifier {

  val Key = ServiceKey[Any]("programClassifier")

  sealed trait ProgramClassifierMessage

  type Trained = (String, String) // text, class
  type Untrained = (String, String) // text, NotUsed
  type ClassAndScore = (String, Double)

  final case class ProgramsClassificationRequest(programs: List[Program], replyTo: ActorRef[ProgramsClassification]) extends ProgramClassifierMessage
  // Client issuing a request to get all untrained data that this classifier knows about since the specified timestamp.
  final case class UntrainedDataRequest(since: Long, replyTo: ActorRef[UntrainedData]) extends ProgramClassifierMessage
  // Client notifying this classifier that new data have been trained
  final case class TrainedDataNotification(trainedData: List[Trained]) extends ProgramClassifierMessage
  // client notifying this classifier that new untrained data have been collected
  final case class UntrainedDataNotification(untrainedData: List[Untrained]) extends ProgramClassifierMessage

  sealed trait ProgramClassifierResponse
  final case class ProgramsClassification(classifications: List[(Program, ClassAndScore)]) extends ProgramClassifierResponse
  final case class UntrainedData(untrainedData: List[Untrained]) extends ProgramClassifierResponse

  // a cdc used to create datum from lines, as well as to create the classifier.
  // However, only the classifier is mutable as it may be retrained. The cdc itself only depends on the properties which don't change
  private val cdc = new ColumnDataClassifier(buildProperties())

  def apply(): Behavior[ProgramClassifierMessage] = {
    val (classifier, trainedDataSize) = readClassifier()
    handle(classifier, List.empty, trainedDataSize)
  }

  def handle(classifier: Classifier[String, String], untrainedData: List[Untrained], trainedDataSize: Integer): Behavior[ProgramClassifierMessage] =
    Behaviors.setup { _ =>
      Behaviors.receiveMessage {
        case ProgramsClassificationRequest(programs, replyTo) =>
          val classifications = programs.map(program => (program, classAndScore(classifier, program.summary)))
          replyTo ! ProgramsClassification(classifications)
          Behaviors.same
        case UntrainedDataRequest(since, replyTo) =>
          replyTo ! UntrainedData(untrainedData)
          Behaviors.same
        case UntrainedDataNotification(newUntrainedData) =>
          handle(classifier, untrainedData:::newUntrainedData, trainedDataSize)
        case TrainedDataNotification(newTrainedData) =>
          if (trainedDataSize < newTrainedData.size) { // todo maybe force a retrain if users trained data are way off what the classifier predicted?
            // retrain the classifier and return a new behavior that uses this new classifier
            val (newClassifier, newDataSize) = retrainClassifier(newTrainedData)
            handle(newClassifier, List.empty, newDataSize)
          } else {
            Behaviors.same
          }
      }
    }

  def classAndScore(classifier: Classifier[String, String], feature: String): ClassAndScore = {
    val datum = programToDatum(feature, "")
    val class_ = classifier.classOf(datum)
    (class_, classifier.scoresOf(datum).getCount(class_))
  }

  private def programToDatum(feature: String, rating: String) =
    cdc.makeDatumFromStrings(Array(rating, feature))

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

  private def retrainClassifier(newTrainedData: List[Trained]): (Classifier[String,String], Int) = {
    // first off, load the persisted trained data. It is not kept in memory to reduce the footprint
    val newDataset: GeneralDataset[String, String] = readObjectFromFile("trained-data.ser.gz")
    newTrainedData.map(tuple => programToDatum(tuple._1, tuple._2)).foreach(newDataset.add)
    writeObjectToFile(newDataset, "trained-data.ser.gz")
    writeObjectToFile(Integer.valueOf(newDataset.size()), "trained-data.size")
    val newClassifier = cdc.makeClassifier(newDataset)
    writeObjectToFile(newClassifier, "classifier.ser.gz")
    (newClassifier, newDataset.size())

  }

  // @VisibleForTesting - may be overriden in tests
  def writeObjectToFile(obj: AnyRef, filename: String): Unit = {
    IOUtils.writeObjectToFile(obj, filename)
  }

  // @VisibleForTesting - may be overriden in tests
  @throws(classOf[IOException])
  def readObjectFromFile[A](filename: String): A = {
    IOUtils.readObjectFromFile(filename)
  }

  private def readClassifier(): (Classifier[String, String], Int) = {
    try {
      (readObjectFromFile("classifier.ser.gz"), readObjectFromFile("trained-data.size"))
    } catch {
      case ioe: IOException => (cdc.makeClassifier(new Dataset()), 0)
    }
  }

}
