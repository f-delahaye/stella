package org.stella.ai

import java.io.FileInputStream
import java.lang
import java.util.Properties

import edu.stanford.nlp.ie.crf.CRFClassifier
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation
import edu.stanford.nlp.ling.CoreLabel.OutputFormat
import edu.stanford.nlp.ling.{CoreAnnotations, CoreLabel}
import edu.stanford.nlp.sequences.SeqClassifierFlags
import edu.stanford.nlp.util.StringUtils
import org.stella.ai.SentenceProcessor.StanfordNlpPath

import scala.collection.JavaConverters._;

object Labeler {
  def label(sentence: String) : List[(String, String)] = {
    label(sentence, loadLabeler)
  }

  /**
    * Returns a List<Pair<String, String>> where each pair is one of the labels available in the training file (first),
    * and the matching text (second)
    * @param sentence
    * @param classifier
    * @return
    */
  def label(sentence: String, classifier: CRFClassifier[CoreLabel]): List[(String, String)] = {
    classifier.classify(sentence).asScala.flatMap(_.asScala).map(lbl => (lbl.word(), lbl.get(classOf[AnswerAnnotation]))).toList
  }

  def trainLabeler(): Unit = {
    val flags = new SeqClassifierFlags(StringUtils.propFileToProperties(StanfordNlpPath + "labeler.props"))
    val classifier = new CRFClassifier[CoreLabel](flags)
    classifier.train(StanfordNlpPath + "labeler.train")
    classifier.serializeClassifier(StanfordNlpPath + "labeler.ser.gz")
  }

  def loadLabeler: CRFClassifier[CoreLabel] = CRFClassifier.getClassifier(StanfordNlpPath + "labeler.ser.gz")
}
