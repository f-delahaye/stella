package org.stella.brain.user

import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource
import org.stella.brain.programs.UntrainedProgramManager
import org.stella.brain.programs.UntrainedProgramManager.UntrainedPrograms

/**
  * Returns an Akka source which generates Untrained data that may be sent to user for manual classification.
  *
  * Untrained data are represented as simple strings.
  * Data retrieved consists of:
  * - an initial list of untrained data which are stored internally for a short period of time (typically a few days worth of data)
  * - any new untrained data collected AFTER the source has been created.
  */

object UntrainedDataUserFlow {

  def createSource(hoursSinceLastConnection: Long, untrainedProgramManager: ActorRef[UntrainedProgramManager.UntrainedProgramManagerMessage])(implicit actorSystem: ActorSystem[Nothing]): Source[String, _] = {
    ActorSource.actorRef[UntrainedPrograms](PartialFunction.empty,PartialFunction.empty, 10, OverflowStrategy.dropHead)
      .mapConcat(_.untrainedData)// extract list from message
      .map(_._1) // extract summary from data
      .mapMaterializedValue(
        actorRef => {
          untrainedProgramManager ! UntrainedProgramManager.UntrainedProgramRequest(hoursSinceLastConnection, actorRef)
          actorSystem.eventStream ! EventStream.Subscribe(actorRef)
        })

  }
}
