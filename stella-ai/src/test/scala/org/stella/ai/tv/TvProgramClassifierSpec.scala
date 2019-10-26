package org.stella.ai.tv

import java.time.LocalTime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.stella.ai.classifier.TextClassifier
import org.stella.ai.tv.TvProgramClassifier.{AskClassifierTvProgramsSelection, SendClientTvProgramsSelection}

class TvProgramClassifierSpec
  extends TestKit(ActorSystem("TvProgramClassifierSpec"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MockFactory {

  val classifier: TextClassifier = mock[TextClassifier]
  var classifierActor: TestActorRef[TvProgramClassifier] = _

    override def afterAll: Unit = {
      TestKit.shutdownActorSystem(system)
    }


    override def beforeEach = {
      classifierActor = TestActorRef(TvProgramClassifier.props(classifier))
    }
  "A TvProgramClassifier actor" must {

    "add trained TvProgram into classifier" in {
      (classifier.add _).expects(List(("this is the summary", "yes")))
      classifierActor ! TvProgramClassifier.SendClassifierDataTraining(List(("this is the summary", "yes")))
      }

      "send empty selection upon no matching" in {
        (classifier.ratingAndScore _).expects("meh thing").returning(("yes", 0.4))
        (classifier.ratingAndScore _).expects("awful stuff").returning(("no", 1))
        classifierActor ! AskClassifierTvProgramsSelection(
          List(TvProgram(LocalTime.now(), "some", "whatever", "meh thing"), TvProgram(LocalTime.now(), "some", "whatever", "awful stuff")))
        expectMsg(SendClientTvProgramsSelection(List()))
      }

      "send selection upon matching" in {
        (classifier.ratingAndScore _).expects("cool thing").returning(("yes", 1))
        val coolProgram = TvProgram(LocalTime.now(), "some", "whatever", "cool thing")
        classifierActor ! AskClassifierTvProgramsSelection(List(coolProgram))
        expectMsg(SendClientTvProgramsSelection(List(coolProgram)))
      }

    }
  }
