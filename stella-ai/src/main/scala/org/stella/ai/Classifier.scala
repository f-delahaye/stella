package org.stella.ai

import java.io.IOException

import edu.stanford.nlp.classify.ColumnDataClassifier
import edu.stanford.nlp.stats.Counter
import org.stella.ai.SentenceProcessor.StanfordNlpPath

object Classifier {

  def classify(sentence: String) : (String, Counter[String]) = {
    classify(sentence, loadClassifier)
  }

  /**
    * Returns a String representing one of the classifications available in the training file.
    *
    * @param sentence
    * @param classifier
    * @return
    */
  def classify(sentence: String, classifier: ColumnDataClassifier): (String, Counter[String]) = {
    val datum = classifier.makeDatumFromLine(sentence)
    (classifier.classOf(datum), classifier.scoresOf(datum))
  }

  @throws[IOException]
  def trainClassifier(): Unit = {
    val classifier = new ColumnDataClassifier(StanfordNlpPath + "classifier.props")
    classifier.trainClassifier(StanfordNlpPath + "classifier.train")
    classifier.serializeClassifier(StanfordNlpPath + "classifier.ser.gz")
  }

  def loadClassifier: ColumnDataClassifier = ColumnDataClassifier.getClassifier(StanfordNlpPath + "classifier.ser.gz")

}
