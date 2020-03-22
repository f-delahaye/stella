package org.stella.brain.programs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.junit.runner.RunWith
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.junit.JUnitRunner
import org.stella.brain.programs.UntrainedProgramManager.{UntrainedPrograms, UntrainedProgramsNotification, UntrainedProgramsRequest}

import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class UntrainedProgramManagerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Untrained program manager" must {

    "send empty untrained data list by default" in {

      val classifier = testKit.spawn(UntrainedProgramManager())
      val probe = testKit.createTestProbe[UntrainedPrograms]

      classifier ! UntrainedProgramsRequest(0L, probe.ref)

      probe.expectMessage(1.second, UntrainedPrograms(List()))
    }

    "store any untrained data notified and send it back upon request" in {

      val classifier = testKit.spawn(UntrainedProgramManager())
      val probe = testKit.createTestProbe[UntrainedPrograms]

      val untrainedData = List("Text to train")

      classifier ! UntrainedProgramsNotification(untrainedData)
      classifier ! UntrainedProgramsRequest(0L, probe.ref)

      probe.expectMessage(1.second, UntrainedPrograms(untrainedData))
    }

    "aggregate all untrained data" in {

      val classifier = testKit.spawn(UntrainedProgramManager())
      val probe = testKit.createTestProbe[UntrainedPrograms]

      val untrainedData1 = List("Text1")
      classifier ! UntrainedProgramsNotification(untrainedData1)

      val untrainedData2 = List("Text2")
      classifier ! UntrainedProgramsNotification(untrainedData2)

      classifier ! UntrainedProgramsRequest(0L, probe.ref)

      probe.expectMessage(1.second, UntrainedPrograms(untrainedData1:::untrainedData2))
    }

  }
}
