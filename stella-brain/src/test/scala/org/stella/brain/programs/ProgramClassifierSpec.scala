package org.stella.brain.programs

import java.time.LocalDateTime
import java.util.Collections

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, BehaviorTestKit}
import edu.stanford.nlp.classify.{Classifier, ColumnDataClassifier, Dataset, GeneralDataset}
import edu.stanford.nlp.io.IOUtils
import edu.stanford.nlp.ling.BasicDatum
import edu.stanford.nlp.stats.Counter
import org.junit.runner.RunWith
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar
import org.stella.brain.programs.ProgramClassifier.ProgramsClassification
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.mockito.verification.VerificationMode

@RunWith(classOf[JUnitRunner])
class ProgramClassifierSpec extends WordSpec
  with BeforeAndAfterAll
  with Matchers
  with MockitoSugar {

  "program classifier" must {

    "retrain classifier initially" in {

      val initialClassifier = mock[Classifier[String, String]]
      val trainedDataset = new Dataset[String, String]()
      val cdc = mock[ColumnDataClassifier]
      val storage = mock[ClassifierStorage]
      when(storage.readTrainedData()).thenReturn(trainedDataset)
      when(storage.readClassifier()).thenReturn((initialClassifier, trainedDataset.size()))

      when(cdc.makeDatumFromStrings(any())).thenReturn(new BasicDatum[String, String](Collections.emptyList()))


      // We use synchronous testing since we only want to test the behavior, not the sending of messages or the creation of child actors
      // https://doc.akka.io/docs/akka/current/typed/testing-sync.html
      val testKit = BehaviorTestKit(ProgramClassifier.create(storage, cdc))

      val retrainedClassifier = mock[Classifier[String, String]]
      when(cdc.makeClassifier(any())).thenReturn(retrainedClassifier)

      testKit.run(ProgramClassifier.TrainedDataNotification(List(("trained", "class"))))

      verify(storage, times(1)).storeClassifier(ArgumentMatchers.eq(retrainedClassifier), argThat[GeneralDataset[String, String]](dataset => dataset.size() == 1))
    }

    "retrain classifier when needed" in {
      val cdc = mock[ColumnDataClassifier]
      when(cdc.makeDatumFromStrings(any())).thenReturn(new BasicDatum[String, String](Collections.emptyList()))

      val storage = mock[ClassifierStorage]
      val initialClassifier = mock[Classifier[String, String]]
      val trainedDataset = new Dataset[String, String]()
      1 to 3 map (_ => new BasicDatum[String, String](Collections.emptyList())) foreach trainedDataset.add
      when(storage.readTrainedData()).thenReturn(trainedDataset)
      when(storage.readClassifier()).thenReturn((initialClassifier, trainedDataset.size()))
      val testKit = BehaviorTestKit(ProgramClassifier.create(storage, cdc))

      // There were 3 persisted trained data, next retrain size should be 5.
      // We add 1 trained data, which makes it 4, we're still on off. no retrain expected
      testKit.run(ProgramClassifier.TrainedDataNotification(List(("trained", "class"))))
      verify(storage, never()).storeClassifier(any(), any())

      val retrainedClassifier = mock[Classifier[String, String]]
      when(cdc.makeClassifier(any())).thenReturn(retrainedClassifier)
      // We add another trained data, which makes it 5, which is a fibonacci value so this should trigger a retrain
      testKit.run(ProgramClassifier.TrainedDataNotification(List(("trained2", "class2"))))

      verify(storage, times(1)).storeClassifier(ArgumentMatchers.eq(retrainedClassifier), argThat[GeneralDataset[String, String]](dataset => dataset.size() == 5))


    }

    "classify upon request" in {

      val testKit = ActorTestKit()

      val program = Program(LocalDateTime.now(), "channel", "title", "item")

      val storage = mock[ClassifierStorage]
      val classifier = mock[Classifier[String, String]]

      when(storage.readClassifier()).thenReturn((classifier, 1))

      val cdc = mock[ColumnDataClassifier]
      val datum = new BasicDatum[String, String](Collections.emptyList())
      when(cdc.makeDatumFromStrings(any())).thenReturn(datum)
      when(classifier.classOf(datum)).thenReturn("class")
      val counter = mock[Counter[String]]
      when(counter.getCount("class")).thenReturn(0.8)
      when(classifier.scoresOf(datum)).thenReturn(counter)

      val probe = testKit.createTestProbe[ProgramsClassification]

      testKit.spawn(ProgramClassifier.create(storage, cdc)) ! ProgramClassifier.ProgramsClassificationRequest(List(program), probe.ref)

      probe.expectMessage(ProgramsClassification(List((program, ("class", 0.8)))))
    }

    }
}
