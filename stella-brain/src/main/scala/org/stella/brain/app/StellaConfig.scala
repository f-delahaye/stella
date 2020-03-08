package org.stella.brain.app

import org.stella.brain.programs.LInternauteOverviewCrawler.RequestMode
import org.stella.brain.programs.LInternauteOverviewCrawler.RequestMode.RequestMode

object StellaConfig {

  private var overridenLinternauteRequestMode: Option[RequestMode] = None

  def getLInternauteRequestMode: RequestMode = overridenLinternauteRequestMode.getOrElse(RequestMode.CacheOrUrl)

  private[app] def setLInternauteRequestMode(mode: RequestMode) = overridenLinternauteRequestMode = Some(mode)

}
