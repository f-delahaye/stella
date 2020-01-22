package org.stella.brain.app

import java.io.File
import java.time.LocalDate

import org.stella.brain.programs.LInternauteOverviewCrawler.LInternauteUrlConnectionProvider

object StellaConfig {

  val testLinternauteFileUrlConnectionProvider: Option[LInternauteUrlConnectionProvider] = Option(linternauteFileUrlConnectionProvider)

  private def linternauteFileUrlConnectionProvider(date: LocalDate, channel: String) = getClass.getResource(s"/programmes/$channel-2020-01-19.html").openConnection()

  private var linternauteUrlConnectionProvider: Option[LInternauteUrlConnectionProvider] = Option.empty

  def getLInternauteUrlConnectionProvider = linternauteUrlConnectionProvider

  private[app] def setLInternauteUrlConnectionProvider(provider: Option[LInternauteUrlConnectionProvider]) = linternauteUrlConnectionProvider = provider

}
