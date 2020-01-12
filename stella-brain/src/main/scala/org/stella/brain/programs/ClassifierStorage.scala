package org.stella.brain.programs

import edu.stanford.nlp.classify.{Classifier, GeneralDataset}
import edu.stanford.nlp.io.IOUtils

trait ClassifierStorage {

  /**
   * Stores a new classifier along with the data it was trained with.
   */
  def storeClassifier(classifier: Classifier[String, String], trainedData: GeneralDataset[String, String]): Unit

  /**
   * Returns the classifier along with the size of the data it was trained with.
   *
   * The second element returned by this method will be the same as the size of the dataset returned by readTrainedData.
   * It doesn't return the trained data itself as it's not needed until the classifier needs retraining. The size is needed however to determine IF the classifier needs retraining
   *
   *
   * @return
   */
  def readClassifier() : (Classifier[String, String], Long)

  /**
   * Returns the data the classifier was trained with. Its size will be the same as the second element of the pair returned by readClassifier.
   * This dataset may be needed for retraining purposes: load current dataset, add the new one, retrain the classifer and persist it
   *
   * @return
   */
  def readTrainedData(): GeneralDataset[String, String]
}

/**
 * Writes to / reads from a file, using Stanford's IOUtils class.
 */
object StanfordFileStorage extends ClassifierStorage {

  override def storeClassifier(classifier: Classifier[String, String], trainedData: GeneralDataset[String, String]): Unit = {
    IOUtils.writeObjectToFile(classifier, "classifier.ser.gz")
    IOUtils.writeObjectToFile(trainedData, "trained-data.ser.gz")
    IOUtils.writeObjectToFile(trainedData.size(), "trained-data.size")
  }

  override def readClassifier() : (Classifier[String, String], Long)  = {
    (IOUtils.readObjectFromFile("classifier.ser.gz"), IOUtils.readObjectFromFile("trained-data.size"))
  }

  override   def readTrainedData(): GeneralDataset[String, String] = IOUtils.readObjectFromFile("trained-data.ser.gz")

}
