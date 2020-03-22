package org.stella.brain.programs

import java.time.LocalDate

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}

import scala.collection.mutable.SortedMap
import scala.collection.mutable.Map

/**
  * Persistent cache to store programs already collected.
  * 
  * Main reasons is to avoid spamming websites to collect the same programs over and over again, especially while writing & testing code.
  * This cache is meant to be used only by *Crawler actors
  * Arguably, the persistence layer could have been moved in the actors themselves, however it is in a separate class for two main reasons:
  * - the logic layer in the Crawler is not trivial, and i did not want to add extra complicity with the persistence layer. Plus, if another crawler is implemented later; would we have to duplicate the persistence logic too?
  * - it allows all other actors in the programs module to be tested using the synchronous testing approach, which i prefer (persistence is currently only testable with the asynchronous toolkit).
  */
object ProgramCache {

  sealed trait Command
  case class LoadProgramsFromCache(date: LocalDate, channel: String, replyTo: ActorRef[ProgramsLoadedFromCache]) extends Command
  case class StoreProgramsInCache(date: LocalDate, channel: String, programs: List[Program]) extends Command
  case class ProgramsLoadedFromCache(date: LocalDate, channel: String, programs: Option[List[Program]])

  // Internal persistent event
  case class ProgramsEvent(date: LocalDate, channel: String, programs: List[Program])
  final case class State(history: SortedMap[LocalDate, Map[String, List[Program]]] = SortedMap.empty)

  // Id could be anything, really ... It is needed in order for tests to not interfere with each other.
  // Same strategy as https://doc.akka.io/docs/akka/current/typed/persistence-testing.html
  def apply(id: String, maxNumberOfDays: Integer=31): Behavior[Command] = {

    def getCachedPrograms(state: State, date: LocalDate, channel: String): Option[List[Program]] = 
      state.history.get(date).flatMap(historyByDate => historyByDate.get(channel))

    def eventHandler(state: State, event: ProgramsEvent): State = {
      val history = state.history
      history.getOrElseUpdate(event.date, Map()) += (event.channel -> event.programs)
      State(if (history.size > maxNumberOfDays) history.drop(1) else history)
    }

    def commandHandler(state: State, message: Command): Effect[ProgramsEvent, State] = {

      message match {
        case LoadProgramsFromCache(date, channel, replyTo) =>
          Effect.none.thenReply(replyTo)(state => ProgramsLoadedFromCache(date, channel, getCachedPrograms(state, date, channel)))
        case StoreProgramsInCache(date, channel, programs) => 
          Effect.persist(ProgramsEvent(date, channel, programs))
      }
    }

  Behaviors.setup { _ =>
    EventSourcedBehavior[Command, ProgramsEvent, State](
      PersistenceId.ofUniqueId(s"program-cache-$id"),
      State(),
      commandHandler,
      eventHandler)
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = maxNumberOfDays, keepNSnapshots = 1)
      .withDeleteEventsOnSnapshot)
  }
}
}
