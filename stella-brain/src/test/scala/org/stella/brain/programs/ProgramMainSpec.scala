package org.stella.brain.programs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.eventstream.EventStream.Publish
import akka.stream.scaladsl.Sink
import org.junit.runner.RunWith
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.junit.JUnitRunner
import org.stella.brain.programs.UntrainedProgramManager.{UntrainedPrograms, UntrainedProgramsNotification}

import scala.concurrent.Await
import scala.concurrent.duration._

// this test requires eventBus, an ActorSource, not sure this may be done with synchronous style
// https://doc.akka.io/docs/akka/current/typed/testing-sync.html
@RunWith(classOf[JUnitRunner])
class ProgramMainSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  implicit val actorSystem = testKit.system

  // Get the implicit ExecutionContext from this import

  "UntrainedPrograms source" must {

    "publish initial data to user" in {
      val manager = testKit.spawn(UntrainedProgramManager())
      val eventStream = testKit.createTestProbe[EventStream.Command]().ref

      val untrainedData = List("initialData")
      manager ! UntrainedProgramsNotification(untrainedData)

      val source = ProgramMain.untrainedProgramsSource(10, manager, eventStream)
      val future = source.take(1).runWith(Sink.seq)
      assert(Await.result(future, 1.second) == List("initialData"))
    }

    "publish newly collected data to user" in {
      val manager = testKit.spawn(UntrainedProgramManager())
      val eventStream = testKit.createTestProbe[EventStream.Command]()

      ProgramMain.untrainedProgramsSource(10, manager, eventStream.ref)

      val untrainedData = List("newData")
      UntrainedProgramManager.publishToEventStream(eventStream.ref, untrainedData)

      eventStream.expectMessage(1.second, Publish(UntrainedPrograms(List("newData"))))
    }
  }
}
