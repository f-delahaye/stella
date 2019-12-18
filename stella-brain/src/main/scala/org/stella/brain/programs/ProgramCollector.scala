package org.stella.brain.programs

import java.time.LocalDate

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

/**
  * Collects program across all supported channels for the specified date.
  */
object ProgramCollector {

  private val TV_CHANNELS = List("histoire-tps")

  sealed trait ProgramCollectorMessage
  final case class ProgramsByDateRequest(date: LocalDate, replyTo: ActorRef[ProgramsByDate]) extends ProgramCollectorMessage
  final case class ProgramsByDateAndChannelAdapted(date: LocalDate, channel: String, programs: List[Program]) extends ProgramCollectorMessage

  final case class ProgramsByDate(date: LocalDate, programs: List[Program])

  def apply(): Behavior[ProgramCollectorMessage] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case request: ProgramsByDateRequest =>
          val adapter = context.messageAdapter[LInternauteOverviewCrawler.LInternauteOverviewTvPrograms](response => ProgramsByDateAndChannelAdapted(response.date, response.channel, response.programs))
          TV_CHANNELS.foreach(channel =>
            context.spawn(LInternauteOverviewCrawler(), "crawler") ! LInternauteOverviewCrawler.LInternauteOverviewRequest(request.date, channel, adapter)
          )
          handle(TV_CHANNELS, List.empty, request.date, request.replyTo).asInstanceOf
      }
    }

/*
  def handle(channels: List[String], programs: List[Program], date: LocalDate, replyTo: ActorRef[CollectedTvProgramsByDate]): Behavior[ProgramCollectorMessage] =
    if (channels.isEmpty) {
      replyTo ! CollectedTvProgramsByDate(date, programs)
      Behaviors.empty
    } else {
      Behaviors.receiveMessage {
        case collectedPrograms: CollectedTvProgramsByDateAndChannelAdapter =>
          handle(channels.diff(List(collectedPrograms.channel)), programs:::collectedPrograms.programs, date, replyTo)
      }
    }
*/

  def handle(channels: List[String], programs: List[Program], date: LocalDate, replyTo: ActorRef[ProgramsByDate]): Behavior[ProgramsByDateAndChannelAdapted] =
    if (channels.isEmpty) {
      replyTo ! ProgramsByDate(date, programs)
      Behaviors.empty
    } else {
      Behaviors.receive { (_, message) =>
          handle(channels.diff(List(message.channel)), programs:::message.programs, date, replyTo)
      }
    }
}

