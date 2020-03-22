package org.stella.brain.programs

import java.time.LocalDate

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}

import java.util.SortedMap
import java.{util => ju}

/**
  * Persistent cache to store programs already collected.
  * 
  * Main reasons is to avoid spamming websites to collect the same programs over and over again, especially while writing & testing code.
  * This cache is meant to be used only by *Crawler actors
  * Arguably, the persistence layer could have been moved in the actors themselves, however it is in a separate class for two main reasons:
  * - the logic layer in the Crawler is not trivial, and i did not want to add extra complicity with the persistence layer. Plus, if another crawler is implemented later; would we have to duplicate the persistence logic too?
  * - it allows all other actors in the programs module to be tested using the synchronous testing approach, which i prefer (persistence is currently only testable with the asynchronous toolkit).
  */
object ProgramCacheMutable {

  sealed trait Command
  case class LoadProgramsFromCache(date: LocalDate, channel: String, replyTo: ActorRef[ProgramsLoadedFromCache]) extends Command
  case class StoreProgramsInCache(date: LocalDate, channel: String, programs: List[Program]) extends Command
  case class ProgramsLoadedFromCache(date: LocalDate, channel: String, programs: Option[List[Program]])

  // Internal persistent event
  case class ProgramsEvent(date: LocalDate, channel: String, programs: List[Program])
  final case class State(history: SortedMap[LocalDate, Map[String, List[Program]]] = new ju.TreeMap[LocalDate, Map[String, List[Program]]]())

  def apply(crawler: String, maxNumberOfDays: Integer=31): Behavior[Command] = {

    def getHistory(state: State, date: LocalDate, channel: String) = 
      state.history.get(date).get(channel)

    def eventHandler(state: State, event: ProgramsEvent): State = {
      // Wish this method would use immutable Maps?
      // It doesn't because
      // a. I could not find something like computeIfAbsent in Scala Map. Im sure something exists, just cant find it
      // b. SO here we are, stuck with java SortedMap ... and I could not find something like drop(1) that removes just the first element in an immutable way. 
      val history = state.history
      System.out.println(s"before: $history")
      history.computeIfAbsent(event.date, d => Map()) + (event.channel -> event.programs)
      if (history.size() > maxNumberOfDays) {
        history.remove(history.firstKey())
      }
      System.out.println(s"after: $history")      
      // at least, return a new State object
      State(history)
    }

    def commandHandler(state: State, message: Command): Effect[ProgramsEvent, State] = {

      message match {
        case LoadProgramsFromCache(date, channel, replyTo) =>
          Effect.none.thenReply(replyTo)(state => ProgramsLoadedFromCache(date, channel, getHistory(state, date, channel)))
        case StoreProgramsInCache(date, channel, programs) => 
          Effect.persist(ProgramsEvent(date, channel, programs))
      }
    }

  Behaviors.setup { _ =>
    EventSourcedBehavior[Command, ProgramsEvent, State](
      PersistenceId.ofUniqueId(s"program-cache-$crawler"),
      State(),
      commandHandler,
      eventHandler)
//      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 31, keepNSnapshots = 1)
//        .withDeleteEventsOnSnapshot)
  }
}
}
