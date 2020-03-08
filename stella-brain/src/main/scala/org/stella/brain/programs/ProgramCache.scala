package org.stella.brain.programs

import java.time.LocalDate

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}

import scala.collection.immutable.SortedMap

object ProgramCache {

  sealed trait Command
  case class LoadProgramsFromCache(date: LocalDate, channel: String, replyTo: ActorRef[ProgramsLoadedFromCache]) extends Command
  case class StoreProgramsInCache(date: LocalDate, channel: String, programs: List[Program]) extends Command
  case class ProgramsLoadedFromCache(date: LocalDate, channel: String, programs: Option[List[Program]])

  // Internal persistent event
  case class ProgramsEvent(date: LocalDate, channel: String, programs: List[Program])
  final case class State(history: SortedMap[(LocalDate, String), List[Program]] = SortedMap.empty)

  def apply(): Behavior[Command] = {
    def eventHandler(state: State, event: ProgramsEvent): State =
      State(state.history.drop(1) + ((event.date, event.channel) -> event.programs))

    def commandHandler(state: State, message: Command): Effect[ProgramsEvent, State] = {

      message match {
        case LoadProgramsFromCache(date, channel, replyTo) => Effect.none.thenReply(replyTo)(state => ProgramsLoadedFromCache(date, channel, state.history.get((date, channel))))
        case StoreProgramsInCache(date, channel, programs) => Effect.persist(ProgramsEvent(date, channel, programs))
      }
    }

  Behaviors.setup { _ =>
    EventSourcedBehavior[Command, ProgramsEvent, State](
      PersistenceId.ofUniqueId("program-cache"),
      State(),
      commandHandler,
      eventHandler)
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 31, keepNSnapshots = 1)
        .withDeleteEventsOnSnapshot)
  }
}
}
