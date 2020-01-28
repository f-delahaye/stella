package org.stella.brain.user

import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource
import org.stella.brain.programs.UntrainedProgramManager
import org.stella.brain.programs.UntrainedProgramManager.UntrainedPrograms



object UntrainedDataUserFlow {

  def createSource(hoursSinceLastConnection: Long, untrainedProgramManager: ActorRef[UntrainedProgramManager.UntrainedProgramManagerMessage])(implicit actorSystem: ActorSystem[Nothing]): Source[String, _] = {
    ActorSource.actorRef[UntrainedPrograms](PartialFunction.empty,PartialFunction.empty, 10, OverflowStrategy.dropHead)
      .mapConcat(_.untrainedPrograms)// extract list from message
      .mapMaterializedValue(
        actorRef => {
          untrainedProgramManager ! UntrainedProgramManager.UntrainedProgramsRequest(hoursSinceLastConnection, actorRef)
          actorSystem.eventStream ! EventStream.Subscribe(actorRef)
        })
  }
}
