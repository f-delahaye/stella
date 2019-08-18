package org.stella.ai.tv

import java.time.LocalTime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest._
import org.stella.ai.tv.TvProgramClassifier.{AskClassifierTvProgramsSelection, SendClientTvProgramsSelection}

class TvProgramClassifierSpec
  extends TestKit(ActorSystem("TvProgramClassifierSpec"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  var classifier: TestActorRef[TvProgramClassifier] = _

    override def afterAll: Unit = {
      TestKit.shutdownActorSystem(system)
    }

    override def beforeEach = {
      classifier = TestActorRef(new TvProgramClassifier())
    }

  "A TvProgramClassifier actor" must {

      "add trained TvProgram into dataset" in {
        classifier ! TvProgramClassifier.SendClassifierDataTraining(List(("this is the summary", "yes")))
        assertResult("yes")(classifier.underlyingActor.trainedData.getDatum(0).label())
      }

      "send empty selection upon no matching" in {
        classifier ! AskClassifierTvProgramsSelection(List(TvProgram(LocalTime.now(), "some", "whatever", "cool thing"), TvProgram(LocalTime.now(), "some", "whatever", "awful stuff")))
        expectMsg(SendClientTvProgramsSelection(List()))
      }

      "send selection upon matching" in {
        val coolSummary = "cool thing"
        val awfulSummary = "awful stuff"
        val coolProgram = TvProgram(LocalTime.now(), "some", "whatever", coolSummary)
        val awfulProgram = TvProgram(LocalTime.now(), "some", "whatever", awfulSummary)
        classifier ! TvProgramClassifier.SendClassifierDataTraining(List((coolSummary, "yes"), (awfulSummary, "no")))
        classifier ! AskClassifierTvProgramsSelection(List(coolProgram, awfulProgram))
        expectMsg(SendClientTvProgramsSelection(List(coolProgram)))
      }

    }
  }
