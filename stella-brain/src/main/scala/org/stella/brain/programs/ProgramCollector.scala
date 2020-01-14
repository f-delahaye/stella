package org.stella.brain.programs

import java.time.LocalDate

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

/**
  * Collects program across all supported channels for the specified date.
  */
object ProgramCollector {

  val TV_CHANNELS = List("histoire-tps")

  sealed trait ProgramCollectorMessage
  final case class ProgramsByDateRequest(date: LocalDate, replyTo: ActorRef[ProgramsByDate]) extends ProgramCollectorMessage
  final case class ProgramsByDateAndChannelAdapted(date: LocalDate, channel: String, programs: List[Program]) extends ProgramCollectorMessage

  final case class ProgramsByDate(date: LocalDate, programs: List[Program])

  def forTv() = apply(TV_CHANNELS)

  def apply(channels: List[String]) =
    handle(channels, null, null)

  def handle(channels: List[String], programs: List[Program], replyTo: ActorRef[ProgramsByDate]): Behavior[ProgramCollectorMessage] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case ProgramsByDateRequest(date, replyTo) =>
          val adapter = context.messageAdapter[LInternauteOverviewCrawler.LInternauteOverviewTvPrograms](response => ProgramsByDateAndChannelAdapted(response.date, response.channel, response.programs))
          channels.foreach(channel =>
            context.spawn(LInternauteOverviewCrawler(), s"crawler for $channel") ! LInternauteOverviewCrawler.LInternauteOverviewRequest(date, channel, adapter)
          )
          handle(channels, List.empty, replyTo)
        case ProgramsByDateAndChannelAdapted(date, channel, progs) =>
          val newPrograms = programs:::progs
          if (channels.size == 1) {
            replyTo ! ProgramsByDate(date, newPrograms)
            Behaviors.stopped
          } else {
            handle(channels.diff(List(channel)), newPrograms, replyTo)
          }
      }
    }
}

