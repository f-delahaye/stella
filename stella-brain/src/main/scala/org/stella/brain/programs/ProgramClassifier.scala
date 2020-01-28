package org.stella.brain.programs

import java.io.IOException
import java.util.Properties

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import edu.stanford.nlp.classify._

import scala.util.Try

/**
 * A classifier which may be used to classify data using pre-trained data.
 *
 * Please note, training is different to classification:
 * the former is performed by user to train the actual classifier, the latter is done autotically using the trained classifier.
 *
 * Hence, in addition to messages to request / send classification, this actor also support a trained data notification.
 * These data will be used to retrain the internal classifier at the actor's discretion.
  */
object ProgramClassifier {

  sealed trait ProgramClassifierMessage

  type Trained = (String, String) // text, class
  type ClassAndScore = (String, Double)

  final case class ProgramsClassificationRequest(programs: List[Program], replyTo: ActorRef[ProgramsClassification]) extends ProgramClassifierMessage
  // Client notifying this classifier that new data have been trained
  final case class TrainedProgramsNotification(trainedData: List[Trained]) extends ProgramClassifierMessage

  sealed trait ProgramClassifierResponse
  final case class ProgramsClassification(classifications: List[(Program, ClassAndScore)]) extends ProgramClassifierResponse


  def apply(): Behavior[ProgramClassifierMessage] = create(StanfordFileStorage, new ColumnDataClassifier(buildProperties()))

  /**
   * Factory method called internally by apply(), which may also be called by tests wth custom storage and cdc.
   *
   * The ColumnDataClassifier is used to create datum from lines, as well as to create the classifier.
   * However, only the classifier is mutable as it may be retrained. The cdc itself only depends on the properties which don't change
   *
   */
  def create(storage: ClassifierStorage, cdc: ColumnDataClassifier): Behavior[ProgramClassifierMessage] = {

    def classAndScore(classifier: Classifier[String, String], feature: String): ClassAndScore = {
      val datum = programToDatum(feature, "")
      val classOf = classifier.classOf(datum)
      (classOf, classifier.scoresOf(datum).getCount(classOf))
    }

    def programToDatum(feature: String, rating: String) =
    cdc.makeDatumFromStrings(Array(rating, feature))

    def readClassifier: (Classifier[String, String], Int) = {
      Try { storage.readClassifier() } getOrElse((cdc.makeClassifier(new Dataset()), 0))
    }

    /**
     * Retrains a new classifier from the current persisted trained data plus the supplied newly trained data.
     */
    def retrainClassifier(newTrainedData: List[Trained]) = {
      // retrain the classifier and return a new behavior that uses this new classifier
      // first off, load the persisted trained data. It is not kept in memory to reduce the footprint
      val newTrainedDataset = Try {storage.readTrainedData} getOrElse(new Dataset[String, String]())

      // Then add in the new trained data
      newTrainedData.map(tuple => programToDatum(tuple._1, tuple._2)).foreach(newTrainedDataset.add)
      val newClassifier = cdc.makeClassifier(newTrainedDataset)
      storage.storeClassifier(newClassifier, newTrainedDataset)
      (newClassifier, calculateCountUntilNextRetrain(newTrainedDataset.size))
    }
    /**
     *
     * @param classifier the current trained classifier to use for classification purposes
     * @param pendingTrainedData  data which have been trained by user but haven't yet been used to retrain the classifier
     * @param countUntilNextRetrain size that pendingTrainedData has to reach before they are used to train a new classifier
     * @return
     */
    def handle(classifier: Classifier[String, String], pendingTrainedData: List[Trained], countUntilNextRetrain: Long): Behavior[ProgramClassifierMessage] =
      Behaviors.setup { _ =>
        Behaviors.receiveMessage {
          case ProgramsClassificationRequest(programs, replyTo) =>
            val classifications = programs.map(program => (program, classAndScore(classifier, program.summary)))
            replyTo ! ProgramsClassification(classifications)
            Behaviors.same
          case TrainedProgramsNotification(newTrainedData) =>
            val newPendingTrainedData: List[Trained] = pendingTrainedData ::: newTrainedData
            if (newPendingTrainedData.size >= countUntilNextRetrain) {
              val (newClassifier, newCountUntilNextRetrain) = retrainClassifier(newPendingTrainedData)
              handle(newClassifier, List.empty, newCountUntilNextRetrain)
            } else {
              handle(classifier, newPendingTrainedData, countUntilNextRetrain)
            }
        }
      }

    val (classifier, trainedDataSize) = readClassifier
    handle(classifier, List.empty, calculateCountUntilNextRetrain(trainedDataSize))

  }

  /**
   * Given a current size of trained data, calculates the number of pending trained data which may be kept in memory before a new classifier is trained.
   *
   * The algorithm uses fibonacci.
   * datasize is one of the values of the suite: f(n).
   * the next value f(n++1) is calculated and then f(n+1) - f(n) is returned
   * For example, if dataSize is 5, f(n+1) is 8 and this method will return 3.
   * If  dataSize is 8,  f(n+1) is 13 and thils method will return 5.
   *
   * Note this method supports dataSize's which are not exact value of the suite. In this case, dataSize will be rounded down to the closest Fibonacci value.
   * For example, if dataSize is 9, f(n) will be rounded to 8 and the method will return 5.
   * This is useful if e.g. current data has 5 elements, the next calculated retrainedSize is 8 but then we receive 4 trained data from users, we end up with a datasize of 9, but we still want the method to return 4 up to the next fibonacci value.
   *
   */
  private def calculateCountUntilNextRetrain(dataSize: Long) = {
    // calculate which element of fibonacci we're at, using binet formula.
    // For example, if f(n) = 13 then n = 7, if f(n) = 21 then n = 8.
    val n = Math.round(Math.log(dataSize * Math.sqrt(5)) / Math.log(1.618))
    // then calculate f(n+1)
    Math.round(Math.pow(1.618, n+1) / Math.sqrt(5)) - dataSize
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

}
