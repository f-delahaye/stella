package org.stella.brain.user

import akka.actor.typed.ActorRef
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.eventstream.EventStream
import akka.stream.scaladsl.Sink
import org.junit.runner.RunWith
import org.scalatest.WordSpecLike
import org.scalatestplus.junit.JUnitRunner
import org.stella.brain.programs.ProgramClassifier
import org.stella.brain.programs.ProgramClassifier.{ProgramClassifierMessage, Untrained, UntrainedData, UntrainedDataNotification}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// this test requires eventBus, an ActorSource, not sure this may be done with synchronous style
// https://doc.akka.io/docs/akka/current/typed/testing-sync.html
@RunWith(classOf[JUnitRunner])
class UntrainedDataUserFlowSpec extends ScalaTestWithActorTestKit with WordSpecLike {

  implicit val actorSystem = testKit.system

  // Get the implicit ExecutionContext from this import

  "UntrainedDataUser source" must {

    "publish initial data to user" in {
      val classifier = testKit.spawn(ProgramClassifier())

      val untrainedData = List(("initialData", "NotCompletedYet"))
      classifier ! UntrainedDataNotification(untrainedData)

      val source = UntrainedDataUserFlow.createSource(10, classifier)
      val future = source.take(1).runWith(Sink.seq)
      assert(Await.result(future, 1.second) == List("initialData"))
    }

    "publish newly collected data to user" in {
      val classifier = testKit.createTestProbe[ProgramClassifierMessage]().ref
      val sinkProbe = testKit.createTestProbe[Seq[String]]

      val source = UntrainedDataUserFlow.createSource(10, classifier)
      pipeTo(source.take(1).runWith(Sink.seq), sinkProbe.ref)

      val untrainedData = List(("newData", "NotCompletedYet"))
      system.eventStream ! EventStream.Publish(UntrainedData(untrainedData))

      sinkProbe.expectMessage(1.second, Vector("newData"))
    }
  }
// Copied from PipeableFuture.
// Can't get this https://stackoverflow.com/questions/45042893/cant-gain-the-pipeto-method-on-scala-concurrent-future/45043787 to work
  def pipeTo[T](future: Future[T], recipient: ActorRef[T]): Future[T] = {
    future.andThen {
      case Success(r) => recipient ! r
      case Failure(exception) => exception.printStackTrace()
    }
  }
}
