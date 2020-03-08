package org.stella.brain.programs

import java.time.{LocalDate, LocalDateTime}

import akka.actor.testkit.typed.Effect.{MessageAdapter, Spawned}
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import akka.actor.typed.eventstream.EventStream
import org.junit.runner.RunWith
import org.scalatest.WordSpecLike
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar
import org.stella.brain.programs.ClassifiedProgramManager.ClassifiedProgramManagerMessage
import org.stella.brain.programs.ProgramClassifier.{ClassAndScore, ProgramClassifierMessage, ProgramsClassificationRequest}
import org.stella.brain.programs.ProgramController.ProgramsByDateAdapted
import org.stella.brain.programs.UntrainedProgramManager.{UntrainedProgramManagerMessage, UntrainedProgramsNotification}

@RunWith(classOf[JUnitRunner])
class ProgramControllerSpec extends ScalaTestWithActorTestKit with WordSpecLike  with MockitoSugar {

  "program controller" must {

    "spawn all expected actors" in {
      val testController = BehaviorTestKit(ProgramController(null, null, null, null, null))

      testController.run(ProgramController.ProgramByDateTick)

      // Spawned asserts equality on childName, Props ... and behavior using reference equality
      // so we can't use expectEffect(Spawned(ProgramCollector(), <name>) as ProgramCollectpr will create a new instance
      // which  while functionally equivalent to the one actually used at runtime, is not ===.

      testController.expectEffectType[MessageAdapter[ProgramCollector.ProgramsByDate, ProgramsByDateAdapted]]
      testController.expectEffectPF{case Spawned(_, name, _) if name == "ProgramCollector" =>}
      // expectEffectxxx polls effects from an internal queue ... so at this point we want to validate that there's no effects that we didn't expect.
      assert(!testController.hasEffects())
    }

    "forward collected programs to downstreams systems" in {
      val testClassifier = TestInbox[ProgramClassifierMessage]()
      val testUntrainedManager = TestInbox[UntrainedProgramManagerMessage]()
      val testEventStream = TestInbox[EventStream.Command]()

      val testController = BehaviorTestKit(ProgramController(testClassifier.ref, testUntrainedManager.ref, null, testEventStream.ref, null))

      val collectedPrograms = List(Program(LocalDateTime.now(), "channel", "title", "summary"))
      testController.run(ProgramController.ProgramsByDateAdapted(LocalDate.now(), collectedPrograms))

      // For this assertion we could use expectMessage ...
      testUntrainedManager.receiveMessage() == UntrainedProgramsNotification(List("summary"))
      // but for that one, we can't :
      // testClassifier.expectMessage(ProgramsClassificationRequest(collectedPrograms, <How to match the adapter??>))
      // so we go with receiveMessage instead and for consistency we use it everywhere
      (testClassifier.receiveMessage(): @unchecked) match { case ProgramsClassificationRequest(programs, _) => assert(programs == collectedPrograms)}
      testEventStream.receiveMessage() == EventStream.Publish(collectedPrograms)
    }

    "forward classified programs to classifier manager" in {
      val testClassifiedManager = TestInbox[ClassifiedProgramManagerMessage]()

      val testController = BehaviorTestKit(ProgramController(null, null, testClassifiedManager.ref, null, null))

      val program: Program = Program(LocalDateTime.now(), "channel", "title", "summary")
      val classifiedPrograms: List[(Program, ClassAndScore)] = List((program, ("class", 1.0)))
      testController.run(ProgramController.ProgramsClassificationAdapted(classifiedPrograms))

      testClassifiedManager.expectMessage(ClassifiedProgramManager.ClassifiedProgramsNotification(List((program, 1.0))))
    }
  }
}