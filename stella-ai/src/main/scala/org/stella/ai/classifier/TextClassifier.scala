package org.stella.ai.classifier

import java.util.Properties

import edu.stanford.nlp.classify.{ColumnDataClassifier, Dataset, GeneralDataset}
import edu.stanford.nlp.io.IOUtils

trait TextClassifier {
  def add(featuresAndRatings: List[(String, String)])
  def ratingAndScore(feature: String): (String, Double)

  /**
    * @deprecated for testing purposes
    */
  @Deprecated
  def debugRatingAndScores(feature: String): String
}

/**
  * Adds retrain capability to a TextClassifier.
  *
  * To make things easier, it also adds persistence capability although this could be in a separate trait
  * (but then it would be a little bit tricky to guarantee that originalTrainedDataSize is set up AFTER the persisted classifier has been loaded (which is the expected behavior else the size would always be 0).
  */
trait RetrainableClassifier extends TextClassifier {

  private val originalTrainedDataSize = trainedDataSize

  load()

  abstract override def add(featuresAndRatings: List[(String, String)]): Unit = {
    super.add(featuresAndRatings)
    // if the dataset has grown by 30%, persist.
    // 30% is an empiric threshold which seems to make sense:
    // persisted  trained   do we want to persist?
    //        10     13     N
    //        10     14     Y
    //        30     40     Y
    if (trainedDataSize > 10 && (trainedDataSize + originalTrainedDataSize) > originalTrainedDataSize * 0.3) {
      retrain()
      persist()
    }
  }

  def load(): Boolean
  def persist(): Boolean
  def retrain(): Unit
  def trainedDataSize: Int
}


/**
  * Implementation of TextClassifier which uses stanford's core-nlp library.
  * All functionalities (retrain / persist / load) are supported but by default they are not wired up together.
  *
  * This means that for example, calling retrain will not automatically persist the classifier, and persisted (if any) classifiers are not loaded at start-up.
  *
  * The following behavior:
  * - automatic loading of any persisted classifier / general dataset
  * - automatic persisting upon a retrain
  * may be automatically added by injecting the RetrainableClassifier trait.
  *
  * Classifier and dataset is persisted using the provided constructor arguments.
  * IO operations from stanford is used, which will automatically zip the file if the name ends with .gz. Objects are converted into byte[] using java serialization.
  * To disable actual disk writing, None's may be passed as path parameters. Alternatively, write(), writeClassifier( )and/or writeTrainedData may be overriden to disable all or some of the logics.
  *
  */
class StanfordClassifier(classifierPath: Option[String]) extends TextClassifier {

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

  var classifier: ColumnDataClassifier = new ColumnDataClassifier(buildProperties())
  var trainedData: GeneralDataset[String,String] = new Dataset[String,String]()

  private def programToDatum(feature: String, rating: String) =
    classifier.makeDatumFromStrings(Array(rating, feature))

  override def add(featuresAndRatings: List[(String, String)]): Unit =
    featuresAndRatings.foreach{ case (feature, rating) => trainedData.add(programToDatum(feature, rating)) }



  override def ratingAndScore(feature: String): (String, Double) = {
    val datum = programToDatum(feature, "")
    val rating = classifier.classOf(datum)
    (rating, classifier.scoresOf(datum).getCount(rating))
  }

  def trainedDataSize: Int = trainedData.size()

  def retrain: Unit = classifier.makeClassifier(trainedData)

  /**
    * @deprecated for testing purposes
    */
  override def debugRatingAndScores(feature: String): String = {
    val datum = programToDatum(feature, "")
    classifier.classOf(datum)+" "+classifier.scoresOf(datum).toString
  }

  def load: Boolean = {
    this.classifier = loadClassifier.getOrElse(classifier)
    this.trainedData = loadTrainedData.getOrElse(trainedData)
    true
  }

  def persist: Boolean = {
    persistClassifier()
    persistTrainedData()
    true
  }

  // By default, persistence is not enabled which in effect makes StanfordClassifier an in memory only classifier,
  // though live training (retraining) is supported.
  // These two methods should be overriden via a subclass or a trait to actually persist to / read from a persistent storage
  def loadClassifier: Option[ColumnDataClassifier] = classifierPath.map(path => ColumnDataClassifier.getClassifier(path+"/classifier.ser.gz"))

  def persistClassifier(): Unit = classifierPath.fold(())(path => classifier.serializeClassifier(path+"/classifier.ser.gz"))

/*  def loadTrainedData: Option[Seq[Datum[String, String]]] = {
    import scala.collection.JavaConverters
    trainedDataPath.map(IOUtils.readLines).map(lines => JavaConverters.iterableAsScalaIterable(lines).map(_.split(",")).map(items => programToDatum(items(0), items(1))).toSeq)
  }*/
  def loadTrainedData: Option[GeneralDataset[String,String]] = classifierPath.map(path => IOUtils.readObjectFromFile(path+"/trained-data.ser.gz"))

  def persistTrainedData(): Unit = classifierPath.fold(())(path => IOUtils.writeObjectToFile(trainedData, path+"/trained-data.ser.gz"))

}
