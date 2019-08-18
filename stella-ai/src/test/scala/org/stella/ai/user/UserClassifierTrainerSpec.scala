package org.stella.ai.user

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.TextMessage
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpecLike}
import org.stella.ai.tv.TvProgramClassifier.SendClassifierDataTraining

class UserClassifierTrainerSpec
  extends TestKit(ActorSystem("UserClassifierTrainerSpec"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  var trainer: TestActorRef[UserClassifierTrainer] = _

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeEach = {
    trainer = TestActorRef(new UserClassifierTrainer())
  }

  "A UserClassifierTrainer actor" must {

    "send correctly formatted trained data" in {
      // Calling subscribe is not mentioned in the current (2.5.3) version of akka, but it is in version 2.0
      // Somehow seems necessary otherwise no message is found.
      // Please note how the actorRef passed is testActor, not trainer
      system.eventStream.subscribe(testActor, classOf[SendClassifierDataTraining])
       trainer ! TextMessage.Strict("cool thing=yes")
      expectMsg(SendClassifierDataTraining(List(("cool thing", "yes"))))
    }
  }

}
